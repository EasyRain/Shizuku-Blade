package com.buwan.shizuku.util.tooltip;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Vector4i;

/**
 * 圆角矩形绘制（26.1）。
 *
 * <p>26.1 的 GUI 只暴露 {@code fill} / {@code blit} 这类高层接口，没有 GUI 阶段的
 * 顶点/自定义着色器通道，所以这里用「逐行扫描 + fill」近似圆角（1.20.1 / 1.21.1 走的是
 * SDF 着色器，圆角是数学上平滑的；本版本受 API 限制只能到 GUI 像素级台阶）。</p>
 *
 * <p>每行按「该行最宽处」取整（保守覆盖）：左边单调铺开，相邻行不会留洞，
 * 同时贴合圆的真实曲率 —— 用行中心采样会得到 3px 级跳跃，弧线会断。</p>
 */
public final class RoundedRectRenderer {

    private RoundedRectRenderer() {
    }

    /** Vector4i = (r, g, b, a)。 */
    private static int argb(Vector4i c) {
        return c.w << 24 | c.x << 16 | c.y << 8 | c.z;
    }

    /** 距某条边 d 像素处，半径 radius 的圆角对应的水平缩进。 */
    private static int inset(float radius, float d) {
        if (radius <= 0.0F || d >= radius) {
            return 0;
        }
        float dd = radius - d;
        float v = radius - (float) Math.sqrt(Math.max(0.0, (double) radius * radius - (double) dd * dd));
        return (int) Math.floor(v);
    }

    private static int[] rowInsets(int dy, int height, float tl, float tr, float br, float bl) {
        // 保守覆盖：顶角取行底边、底角取行顶边，保证每行覆盖该行的最宽处
        float top = dy + 1.0F;
        float bottom = height - dy - 1.0F;
        int left = 0;
        int right = 0;
        if (tl > 0.0F && top < tl) {
            left = inset(tl, top);
        }
        if (tr > 0.0F && top < tr) {
            right = inset(tr, top);
        }
        if (bl > 0.0F && bottom < bl) {
            left = Math.max(left, inset(bl, bottom));
        }
        if (br > 0.0F && bottom < br) {
            right = Math.max(right, inset(br, bottom));
        }
        return new int[]{left, right};
    }

    public static void renderRoundedRect(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                                         float topLeft, float topRight, float bottomRight, float bottomLeft,
                                         Vector4i color, int segments) {
        int c = argb(color);
        int x0 = (int) x;
        int y0 = (int) y;
        int w = (int) width;
        int h = (int) height;
        if (w <= 0 || h <= 0) {
            return;
        }
        for (int dy = 0; dy < h; dy++) {
            int[] in = rowInsets(dy, h, topLeft, topRight, bottomRight, bottomLeft);
            int left = x0 + in[0];
            int right = x0 + w - in[1];
            if (right > left) {
                graphics.fill(left, y0 + dy, right, y0 + dy + 1, c);
            }
        }
    }

    public static void renderRoundedRectGradient(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                                                 float topLeft, float topRight, float bottomRight, float bottomLeft,
                                                 GradientConfig gradient, Vector4i fallbackColor, int segments,
                                                 net.minecraft.client.renderer.rendertype.RenderType renderType) {
        renderRoundedRect(graphics, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft,
                fallbackColor, segments);
    }

    public static void renderRoundedRectBorder(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                                               float topLeft, float topRight, float bottomRight, float bottomLeft,
                                               float borderWidth, Vector4i color, int segments) {
        int c = argb(color);
        int x0 = (int) x;
        int y0 = (int) y;
        int w = (int) width;
        int h = (int) height;
        if (w <= 0 || h <= 0) {
            return;
        }
        int b = Math.max(1, Math.round(borderWidth));
        if (w <= 2 * b || h <= 2 * b) {
            graphics.fill(x0, y0, x0 + w, y0 + h, c);
            return;
        }
        int iH = h - 2 * b;
        for (int dy = 0; dy < h; dy++) {
            int[] o = rowInsets(dy, h, topLeft, topRight, bottomRight, bottomLeft);
            int oL = x0 + o[0];
            int oR = x0 + w - o[1];
            if (oR <= oL) {
                continue;
            }
            if (dy < b || dy >= h - b) {
                graphics.fill(oL, y0 + dy, oR, y0 + dy + 1, c);
            } else {
                int[] i = rowInsets(dy - b, iH,
                        Math.max(0.0F, topLeft - b), Math.max(0.0F, topRight - b),
                        Math.max(0.0F, bottomRight - b), Math.max(0.0F, bottomLeft - b));
                int iL = x0 + b + i[0];
                int iR = x0 + w - b - i[1];
                if (iL > oL) {
                    graphics.fill(oL, y0 + dy, Math.min(iL, oR), y0 + dy + 1, c);
                }
                if (iR < oR) {
                    graphics.fill(Math.max(iR, oL), y0 + dy, oR, y0 + dy + 1, c);
                }
            }
        }
    }

    public static void renderRoundedRectBorderGradient(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                                                       float topLeft, float topRight, float bottomRight, float bottomLeft,
                                                       float borderWidth, GradientConfig gradient, Vector4i fallbackColor,
                                                       int segments) {
        renderRoundedRectBorder(graphics, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft,
                borderWidth, fallbackColor, segments);
    }
}
