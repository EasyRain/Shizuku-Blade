package com.buwan.shizuku.util.tooltip;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Vector4i;

/**
 * 圆角矩形绘制。
 * 26.1 移除了 GUI 阶段的自定义 BufferBuilder/RenderSystem 路径，
 * 这里用 {@link GuiGraphicsExtractor#fill} 画普通矩形（圆角简化为直角，保持调用 API 不变）。
 */
public final class RoundedRectRenderer {

    private RoundedRectRenderer() {
    }

    /** Vector4i = (r, g, b, a)。 */
    private static int argb(Vector4i c) {
        return c.w << 24 | c.x << 16 | c.y << 8 | c.z;
    }

    public static void renderRoundedRect(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                                         float topLeft, float topRight, float bottomRight, float bottomLeft,
                                         Vector4i color, int segments) {
        graphics.fill((int) x, (int) y, (int) (x + width), (int) (y + height), argb(color));
    }

    public static void renderRoundedRectGradient(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                                                 float topLeft, float topRight, float bottomRight, float bottomLeft,
                                                 GradientConfig gradient, Vector4i fallbackColor, int segments,
                                                 net.minecraft.client.renderer.rendertype.RenderType renderType) {
        graphics.fill((int) x, (int) y, (int) (x + width), (int) (y + height), argb(fallbackColor));
    }

    public static void renderRoundedRectBorder(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                                               float topLeft, float topRight, float bottomRight, float bottomLeft,
                                               float borderWidth, Vector4i color, int segments) {
        int c = argb(color);
        int x0 = (int) x;
        int y0 = (int) y;
        int x1 = (int) (x + width);
        int y1 = (int) (y + height);
        int b = Math.max(1, (int) borderWidth);
        graphics.fill(x0, y0, x1, y0 + b, c);
        graphics.fill(x0, y1 - b, x1, y1, c);
        graphics.fill(x0, y0, x0 + b, y1, c);
        graphics.fill(x1 - b, y0, x1, y1, c);
    }

    public static void renderRoundedRectBorderGradient(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                                                       float topLeft, float topRight, float bottomRight, float bottomLeft,
                                                       float borderWidth, GradientConfig gradient, Vector4i fallbackColor,
                                                       int segments) {
        renderRoundedRectBorder(graphics, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft,
                borderWidth, fallbackColor, segments);
    }
}
