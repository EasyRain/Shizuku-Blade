package com.buwan.shizuku.client.tooltip;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时生成的圆角面板贴图（4x4 超采样抗锯齿）。
 *
 * <p>26.1 移除了 {@code ShaderInstance}，没法再用 1.20.1 那套 SDF 着色器画平滑圆角；
 * 而 GUI 的 {@code fill} 只有整像素，扫描线法必然出现台阶。这里改为运行时按需要的尺寸
 * 生成一张带抗锯齿的贴图，直接用 {@code blit} 1:1 画出来 —— 圆角平滑、边框还能带渐变。</p>
 *
 * <p>贴图按（宽、高、圆角、边框宽度、填充色、边框色列表）缓存，超量时整体清空重建。</p>
 */
public final class DynamicRoundedPanel {

    private static final int MAX_CACHE = 48;
    private static final Map<Long, Identifier> CACHE = new HashMap<>();
    private static int counter;

    private DynamicRoundedPanel() {
    }

    /**
     * @param fill        填充色 ARGB（alpha=0 表示只画边框）
     * @param borderColors 边框颜色列表（按周向渐变；单色也会正常工作）
     */
    public static Identifier get(int width, int height, float radius, float thickness,
                                 int fill, List<Integer> borderColors) {
        long key = ((long) width << 40) ^ ((long) height << 24)
                ^ ((long) (radius * 4) << 16) ^ ((long) (thickness * 4) << 8)
                ^ (fill & 0xFFFFFFFFL) ^ (borderColors.hashCode() & 0xFFL);
        Identifier cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Identifier id = build(width, height, radius, thickness, fill, borderColors, key);
        if (CACHE.size() >= MAX_CACHE) {
            CACHE.clear();
        }
        CACHE.put(key, id);
        return id;
    }

    private static Identifier build(int width, int height, float radius, float thickness,
                                    int fill, List<Integer> borderColors, long key) {
        NativeImage image = new NativeImage(width, height, true);
        float outerRadius = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
        float innerRadius = Math.max(0.0F, outerRadius - thickness);
        int samples = 4;
        float step = 1.0F / samples;

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                float inside = 0.0F;
                float interior = 0.0F;
                float sumX = 0.0F;
                float sumY = 0.0F;
                for (int sy = 0; sy < samples; sy++) {
                    for (int sx = 0; sx < samples; sx++) {
                        float fx = px + (sx + 0.5F) * step;
                        float fy = py + (sy + 0.5F) * step;
                        if (insideRounded(fx, fy, 0.0F, 0.0F, width, height, outerRadius)) {
                            inside += 1.0F;
                            sumX += fx;
                            sumY += fy;
                        }
                        if (insideRounded(fx, fy, thickness, thickness,
                                width - thickness * 2.0F, height - thickness * 2.0F, innerRadius)) {
                            interior += 1.0F;
                        }
                    }
                }
                float total = samples * samples;
                float outerCoverage = inside / total;
                float innerCoverage = interior / total;
                float borderCoverage = Math.max(0.0F, outerCoverage - innerCoverage);

                int argb = 0;
                if (borderCoverage > 0.0F && borderColors != null && !borderColors.isEmpty()) {
                    int borderColor = borderColorAt(sumX / Math.max(1.0F, inside),
                            sumY / Math.max(1.0F, inside), width, height, borderColors);
                    argb = blend(argb, borderColor, borderCoverage * ((borderColor >>> 24) / 255.0F));
                }
                if (innerCoverage > 0.0F) {
                    argb = blend(argb, fill, innerCoverage * ((fill >>> 24) / 255.0F));
                }
                image.setPixelABGR(px, py, argb);
            }
        }

        Identifier id = Identifier.fromNamespaceAndPath("shizuku", "dynamic/panel_" + (counter++));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "shizuku panel", image));
        return id;
    }

    /** 圆角矩形内部判定（含圆角半径）。 */
    private static boolean insideRounded(float fx, float fy, float ox, float oy, float w, float h, float r) {
        if (w <= 0.0F || h <= 0.0F) {
            return false;
        }
        float left = ox + r;
        float top = oy + r;
        float right = ox + w - r;
        float bottom = oy + h - r;
        float dx = Math.max(Math.max(left - fx, 0.0F), fx - right);
        float dy = Math.max(Math.max(top - fy, 0.0F), fy - bottom);
        return dx * dx + dy * dy <= r * r || (fx >= ox && fx <= ox + w && fy >= oy && fy <= oy + h
                && (fx >= left || fy >= top) && (fx <= right || fy <= bottom));
    }

    /** 边框按周向角度在颜色列表间插值。 */
    private static int borderColorAt(float px, float py, int width, int height, List<Integer> colors) {
        float cx = width * 0.5F;
        float cy = height * 0.5F;
        double angle = Math.atan2(py - cy, px - cx);
        double normalized = (angle + Math.PI) / (Math.PI * 2.0);
        float scaled = (float) (normalized * colors.size());
        int i0 = (int) Math.floor(scaled) % colors.size();
        int i1 = (i0 + 1) % colors.size();
        float t = scaled - (float) Math.floor(scaled);
        return lerpColor(colors.get(i0), colors.get(i1), t);
    }

    private static int lerpColor(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        int al = (int) (aa + (ba - aa) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    /** source over 合成（src/dst 均为 ARGB）。 */
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
        return ((int) (outA * 255.0F) << 24) | ((int) (r * 255.0F) << 16) | ((int) (g * 255.0F) << 8)
                | (int) (b * 255.0F);
    }
}
