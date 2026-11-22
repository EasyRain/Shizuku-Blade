package com.buwan.shizuku.util.tooltip;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector4i;

/**
 * 圆角矩形绘制（1.21 版，纯 {@link GuiGraphics#fill} 实现）。
 *
 * <p><b>为什么不再自己写顶点：</b>1.21 的 {@code RenderType.gui()} 是 QUADS 模式，
 * 而圆角扇/环是三角形几何，塞进去会错位（缺块 + 斜条）；改成自建 TRIANGLES 缓冲后
 * 在 tooltip 渲染阶段又完全画不出来（推测与该阶段的 buffer/状态有关）。
 * 所以这里退回最稳的原版 API：{@code GuiGraphics#fill} 逐行扫描，
 * 与 {@code JudgmentScreen} 里那套圆角绘制完全同源，已被验证可用。</p>
 *
 * <p>圆角由逐行内缩量算出：中间行 inset=0，圆角区按 r - sqrt(r² - dy²) 收敛。</p>
 */
public final class RoundedRectRenderer {

    private RoundedRectRenderer() {
    }

    private static int argb(Vector4i c) {
        return c.w << 24 | c.x << 16 | c.y << 8 | c.z;
    }

    /** 某一行的水平内缩量。 */
    private static int rowInset(int row, int height, int radius) {
        if (radius <= 0) {
            return 0;
        }
        int dy;
        if (row < radius) {
            dy = radius - row;
        } else if (row >= height - radius) {
            dy = row - (height - radius) + 1;
        } else {
            return 0;
        }
        if (dy > radius) {
            dy = radius;
        }
        return radius - (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
    }

    /** 实心圆角矩形（逐行 fill）。 */
    public static void renderRoundedRect(GuiGraphics graphics, float x, float y, float width, float height,
                                         float topLeft, float topRight, float bottomRight, float bottomLeft,
                                         Vector4i color, int segments) {
        fillRounded(graphics, x, y, width, height, maxRadius(topLeft, topRight, bottomRight, bottomLeft, width, height), argb(color));
    }

    public static void renderRoundedRectGradient(GuiGraphics graphics, float x, float y, float width, float height,
                                                 float topLeft, float topRight, float bottomRight, float bottomLeft,
                                                 GradientConfig gradient, Vector4i fallbackColor, int segments,
                                                 RenderType renderType) {
        renderRoundedRect(graphics, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft,
                fallbackColor, segments);
    }

    /** 圆角描边：外层圆角矩形与内层圆角矩形的差集，逐行画左右两段。 */
    public static void renderRoundedRectBorder(GuiGraphics graphics, float x, float y, float width, float height,
                                               float topLeft, float topRight, float bottomRight, float bottomLeft,
                                               float borderWidth, Vector4i color, int segments) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        int w = Math.round(width);
        int h = Math.round(height);
        int b = Math.max(1, Math.round(borderWidth));
        int outerR = maxRadius(topLeft, topRight, bottomRight, bottomLeft, width, height);
        int innerR = Math.max(0, outerR - b);
        int innerW = w - 2 * b;
        int innerH = h - 2 * b;
        if (innerW <= 0 || innerH <= 0) {
            graphics.fill(Math.round(x), Math.round(y), Math.round(x) + w, Math.round(y) + h, argb(color));
            return;
        }
        int colorArgb = argb(color);
        int left = Math.round(x);
        int top = Math.round(y);
        for (int row = 0; row < h; row++) {
            int outerInset = rowInset(row, h, outerR);
            int innerRow = row - b;
            int innerInset = (innerRow >= 0 && innerRow < innerH) ? b + rowInset(innerRow, innerH, innerR) : -1;
            int rowY = top + row;
            if (innerInset < 0) {
                // 上/下边带：整段都是边框
                graphics.fill(left + outerInset, rowY, left + w - outerInset, rowY + 1, colorArgb);
            } else {
                // 左右两条竖边
                graphics.fill(left + outerInset, rowY, left + innerInset, rowY + 1, colorArgb);
                graphics.fill(left + w - innerInset, rowY, left + w - outerInset, rowY + 1, colorArgb);
            }
        }
    }

    public static void renderRoundedRectBorderGradient(GuiGraphics graphics, float x, float y, float width, float height,
                                                       float topLeft, float topRight, float bottomRight, float bottomLeft,
                                                       float borderWidth, GradientConfig gradient, Vector4i fallbackColor,
                                                       int segments) {
        renderRoundedRectBorder(graphics, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft,
                borderWidth, fallbackColor, segments);
    }

    private static void fillRounded(GuiGraphics graphics, float x, float y, float width, float height, int radius, int color) {
        int w = Math.round(width);
        int h = Math.round(height);
        if (w <= 0 || h <= 0) {
            return;
        }
        int left = Math.round(x);
        int top = Math.round(y);
        for (int row = 0; row < h; row++) {
            int inset = rowInset(row, h, radius);
            graphics.fill(left + inset, top + row, left + w - inset, top + row + 1, color);
        }
    }

    private static int maxRadius(float tl, float tr, float br, float bl, float width, float height) {
        float max = Math.min(Math.max(tl, Math.max(tr, Math.max(br, bl))), Math.min(width, height) / 2.0F);
        return Math.max(0, Math.round(max));
    }
}
