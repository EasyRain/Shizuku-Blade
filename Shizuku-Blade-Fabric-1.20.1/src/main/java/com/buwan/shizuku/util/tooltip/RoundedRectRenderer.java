package com.buwan.shizuku.util.tooltip;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Vector4i;

/**
 * 圆角矩形绘制（1.20.1）。
 *
 * <p>用「逐行扫描 + GuiGraphics.fill」实现圆角：每行按四个角的半径计算左右缩进，
 * 这样既能得到真正的圆角，又不需要 Tesselator 直绘（后者在 GuiGraphics 中途会被状态覆盖）。</p>
 */
public final class RoundedRectRenderer {

    private RoundedRectRenderer() {
    }

    /** Vector4i = (r, g, b, a)。 */
    private static int argb(Vector4i c) {
        return c.w << 24 | c.x << 16 | c.y << 8 | c.z;
    }

    /** 计算某一行在四个圆角约束下的左右缩进。 */
    private static int[] insets(int dy, int height, int tl, int tr, int br, int bl) {
        int left = 0;
        int right = 0;
        if (tl > 0 && dy < tl) {
            left = tl - (int) Math.sqrt(Math.max(0, (double) tl * tl - (double) (tl - dy) * (tl - dy)));
        }
        if (tr > 0 && dy < tr) {
            right = tr - (int) Math.sqrt(Math.max(0, (double) tr * tr - (double) (tr - dy) * (tr - dy)));
        }
        int bottomY = height - 1 - dy;
        if (bl > 0 && bottomY < bl) {
            int v = bl - (int) Math.sqrt(Math.max(0, (double) bl * bl - (double) (bl - bottomY) * (bl - bottomY)));
            left = Math.max(left, v);
        }
        if (br > 0 && bottomY < br) {
            int v = br - (int) Math.sqrt(Math.max(0, (double) br * br - (double) (br - bottomY) * (br - bottomY)));
            right = Math.max(right, v);
        }
        return new int[]{left, right};
    }

    public static void renderRoundedRect(GuiGraphics graphics, float x, float y, float width, float height,
                                         float topLeft, float topRight, float bottomRight, float bottomLeft,
                                         Vector4i color, int segments) {
        int c = argb(color);
        int x0 = (int) x;
        int y0 = (int) y;
        int w = (int) width;
        int h = (int) height;
        int tl = (int) topLeft;
        int tr = (int) topRight;
        int br = (int) bottomRight;
        int bl = (int) bottomLeft;
        for (int dy = 0; dy < h; dy++) {
            int[] in = insets(dy, h, tl, tr, br, bl);
            graphics.fill(x0 + in[0], y0 + dy, x0 + w - in[1], y0 + dy + 1, c);
        }
    }

    public static void renderRoundedRectGradient(GuiGraphics graphics, float x, float y, float width, float height,
                                                 float topLeft, float topRight, float bottomRight, float bottomLeft,
                                                 GradientConfig gradient, Vector4i fallbackColor, int segments,
                                                 net.minecraft.client.renderer.RenderType renderType) {
        renderRoundedRect(graphics, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft,
                fallbackColor, segments);
    }

    public static void renderRoundedRectBorder(GuiGraphics graphics, float x, float y, float width, float height,
                                               float topLeft, float topRight, float bottomRight, float bottomLeft,
                                               float borderWidth, Vector4i color, int segments) {
        int c = argb(color);
        int x0 = (int) x;
        int y0 = (int) y;
        int w = (int) width;
        int h = (int) height;
        int b = Math.max(1, (int) borderWidth);
        int tl = (int) topLeft;
        int tr = (int) topRight;
        int br = (int) bottomRight;
        int bl = (int) bottomLeft;
        for (int dy = 0; dy < h; dy++) {
            int[] in = insets(dy, h, tl, tr, br, bl);
            int left = x0 + in[0];
            int right = x0 + w - in[1];
            if (dy < b || dy >= h - b) {
                graphics.fill(left, y0 + dy, right, y0 + dy + 1, c);
            } else {
                graphics.fill(left, y0 + dy, left + b, y0 + dy + 1, c);
                graphics.fill(right - b, y0 + dy, right, y0 + dy + 1, c);
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
}
