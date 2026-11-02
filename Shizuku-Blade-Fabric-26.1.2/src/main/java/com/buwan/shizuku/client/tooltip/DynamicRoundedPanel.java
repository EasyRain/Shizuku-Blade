package com.buwan.shizuku.client.tooltip;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时生成的圆角面板贴图（解析式抗锯齿）。
 *
 * <p>26.1 移除了 {@code ShaderInstance}，没法复用 1.20.1 的 SDF 着色器；GUI 的 {@code fill}
 * 又只有整像素，扫描线法在圆角处必然出现台阶。这里用「有向距离场 + 覆盖率」直接算每个像素的
 * alpha，生成一张贴图后 1:1 绘制 —— 与 SDF 着色器同样的边缘质量，但不需要碰新的 RenderPipeline。</p>
 *
 * <p>关键点：</p>
 * <ul>
 *   <li>覆盖率用 {@code clamp(0.5 - sd, 0, 1)}（sd 单位是像素），得到约 1 像素宽的平滑过渡；</li>
 *   <li>圆角矩形的 SDF 对称，四角一致；</li>
 *   <li>{@link NativeImage#setPixelABGR} 的打包顺序是 {@code 0xAABBGGRR}，
 *       传参前必须从 ARGB 换序，否则红蓝互换（会显示成黄色）。</li>
 * </ul>
 */
public final class DynamicRoundedPanel {

    private static final int MAX_CACHE = 48;
    private static final Map<Long, Identifier> CACHE = new HashMap<>();
    private static int counter;

    private DynamicRoundedPanel() {
    }

    /** 物理像素倍率（GUI 缩放）：贴图按这个倍率生成，否则 1px 抗锯齿会被放大成一片模糊。 */
    private static int currentScale() {
        try {
            return Math.max(1, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
        } catch (Exception e) {
            return 1;
        }
    }

    public static Identifier get(int width, int height, float radius, float thickness,
                                 int fill, List<Integer> borderColors) {
        int scale = currentScale();
        return getScaled(width, height, radius, thickness, fill, borderColors, scale);
    }

    /** 返回贴图的像素尺寸（宽, 高），调用方按 GUI 尺寸绘制即可。 */
    public static int[] getTexSize(int width, int height) {
        int scale = currentScale();
        return new int[]{width * scale, height * scale};
    }

    private static Identifier getScaled(int width, int height, float radius, float thickness,
                                        int fill, List<Integer> borderColors, int scale) {
        int[] colors = borderColors == null ? new int[0] : borderColors.stream().mapToInt(Integer::intValue).toArray();
        long key = ((long) scale << 56) ^ ((long) width << 42) ^ ((long) height << 26)
                ^ ((long) (radius * 4) << 18) ^ ((long) (thickness * 4) << 10)
                ^ (fill & 0xFFFFFFFFL) ^ java.util.Arrays.hashCode(colors);
        Identifier cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Identifier id = build(width * scale, height * scale, radius * scale, thickness * scale, fill, colors);
        if (CACHE.size() >= MAX_CACHE) {
            CACHE.clear();
        }
        CACHE.put(key, id);
        return id;
    }

    private static Identifier build(int width, int height, float radius, float thickness,
                                    int fill, int[] borderColors) {
        NativeImage image = new NativeImage(width, height, true);
        float outerRadius = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
        float innerRadius = Math.max(0.0F, outerRadius - thickness);
        float innerW = width - thickness * 2.0F;
        float innerH = height - thickness * 2.0F;

        // 调色板里的颜色是 0xRRGGBB（无 alpha 字节 = 全透明），
        // 边框现在由贴图自带 alpha，必须补成不透明，否则整条边框会消失。
        int[] borderColorsFixed = new int[borderColors.length];
        for (int i = 0; i < borderColors.length; i++) {
            borderColorsFixed[i] = opaque(borderColors[i]);
        }
        borderColors = borderColorsFixed;

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                float cx = px + 0.5F;
                float cy = py + 0.5F;

                float dOuter = sdRoundRect(cx, cy, width, height, outerRadius);
                float outerCoverage = clamp01(0.5F - dOuter);
                float innerCoverage = 0.0F;
                if (innerW > 0.0F && innerH > 0.0F) {
                    // 内矩形从 (thickness, thickness) 开始，点必须平移到它的局部坐标；
                    // 直接按 (cx,cy) 算会把它当成居中，导致边框在右下变厚、左上消失。
                    float dInner = sdRoundRect(cx - thickness, cy - thickness, innerW, innerH, innerRadius);
                    innerCoverage = clamp01(0.5F - dInner);
                }
                float borderCoverage = Math.max(0.0F, outerCoverage - innerCoverage);

                int argb = 0;
                if (borderCoverage > 0.0F && borderColors.length > 0) {
                    int borderColor = borderColorAt(cx, cy, width, height, borderColors);
                    argb = blend(argb, borderColor, borderCoverage);
                }
                if (innerCoverage > 0.0F) {
                    argb = blend(argb, fill, innerCoverage);
                }
                image.setPixelABGR(px, py, toAbgr(argb));
            }
        }

        Identifier id = Identifier.fromNamespaceAndPath("shizuku", "dynamic/panel_" + (counter++));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "shizuku panel", image));
        return id;
    }

    /** 圆角矩形的有向距离（<0 在内部），单位：像素。 */
    private static float sdRoundRect(float px, float py, float w, float h, float r) {
        float qx = Math.abs(px - w * 0.5F) - (w * 0.5F - r);
        float qy = Math.abs(py - h * 0.5F) - (h * 0.5F - r);
        float ax = Math.max(qx, 0.0F);
        float ay = Math.max(qy, 0.0F);
        return (float) Math.sqrt(ax * ax + ay * ay) + Math.min(Math.max(qx, qy), 0.0F) - r;
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
    }

    /** 边框颜色按周向角度在颜色列表间插值。 */
    private static int borderColorAt(float px, float py, int width, int height, int[] colors) {
        double angle = Math.atan2(py - height * 0.5F, px - width * 0.5F);
        double normalized = (angle + Math.PI) / (Math.PI * 2.0);
        float scaled = (float) (normalized * colors.length);
        int i0 = (int) Math.floor(scaled) % colors.length;
        int i1 = (i0 + 1) % colors.length;
        float t = scaled - (float) Math.floor(scaled);
        return lerpColor(colors[i0], colors[i1], t);
    }

    private static int lerpColor(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        int al = (int) (aa + (ba - aa) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    /** source over 合成（ARGB in / ARGB out）。 */
    private static int blend(int dst, int src, float coverage) {
        float sa = ((src >>> 24) & 0xFF) / 255.0F * coverage;
        if (sa <= 0.0F) {
            return dst;
        }
        float da = ((dst >>> 24) & 0xFF) / 255.0F;
        float outA = sa + da * (1.0F - sa);
        if (outA <= 0.0F) {
            return 0;
        }
        float sr = ((src >> 16) & 0xFF) / 255.0F;
        float sg = ((src >> 8) & 0xFF) / 255.0F;
        float sb = (src & 0xFF) / 255.0F;
        float dr = ((dst >> 16) & 0xFF) / 255.0F;
        float dg = ((dst >> 8) & 0xFF) / 255.0F;
        float db = (dst & 0xFF) / 255.0F;
        float r = (sr * sa + dr * da * (1.0F - sa)) / outA;
        float g = (sg * sa + dg * da * (1.0F - sa)) / outA;
        float b = (sb * sa + db * da * (1.0F - sa)) / outA;
        return ((int) (outA * 255.0F) << 24) | ((int) (r * 255.0F) << 16)
                | ((int) (g * 255.0F) << 8) | (int) (b * 255.0F);
    }

    /** 无色高位（0xRRGGBB）时补成不透明。 */
    private static int opaque(int color) {
        return ((color >>> 24) == 0 ? 0xFF000000 : (color & 0xFF000000)) | (color & 0xFFFFFF);
    }

    /** ARGB -> 0xAABBGGRR（NativeImage#setPixelABGR 期望的打包顺序）。 */
    private static int toAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
