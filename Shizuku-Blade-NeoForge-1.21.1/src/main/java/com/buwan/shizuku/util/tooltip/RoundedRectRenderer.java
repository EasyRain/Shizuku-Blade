package com.buwan.shizuku.util.tooltip;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector4i;

/**
 * 圆角矩形绘制（1.21 版，多边形几何）。
 *
 * <p><b>为什么必须走顶点而不是 {@code graphics.fill}：</b>fill 只能落在整数像素上，
 * 圆弧永远是 1px 台阶（锯齿明显）；原版 Forge 端也是走顶点，所以圆角是真正的斜线段。</p>
 *
 * <p><b>为什么要 {@code setNormal}：</b>1.21 的 {@code RenderType.gui()} 顶点格式是
 * POSITION_COLOR_NORMAL，缺法线的顶点会被整批丢弃 ⇒ 曾出现「什么都不画」。
 * 另外该 RenderType 的模式是 <b>QUADS</b>，所以每段都按 4 个顶点写（第 4 个与第 3 个重合，
 * 退化三角形不产生二次混合），三角形几何才不会被解析错位。</p>
 */
public final class RoundedRectRenderer {

    private static final int MIN_SEGMENTS = 2;
    private static final int MAX_SEGMENTS = 64;

    private RoundedRectRenderer() {
    }

    private static int argb(Vector4i c) {
        return c.w << 24 | c.x << 16 | c.y << 8 | c.z;
    }

    private static int segments(int segments) {
        return Math.min(MAX_SEGMENTS, Math.max(MIN_SEGMENTS, segments));
    }

    /** 圆角矩形的边界点（顺时针）。 */
    private static List<float[]> boundary(float x, float y, float w, float h,
                                          float tl, float tr, float br, float bl, int n) {
        float maxR = Math.min(w, h) / 2.0F;
        tl = Math.min(Math.max(0.0F, tl), maxR);
        tr = Math.min(Math.max(0.0F, tr), maxR);
        br = Math.min(Math.max(0.0F, br), maxR);
        bl = Math.min(Math.max(0.0F, bl), maxR);
        List<float[]> pts = new ArrayList<>((n + 1) * 4);
        addArc(pts, x + tl, y + tl, tl, 180.0, 270.0, n);
        addArc(pts, x + w - tr, y + tr, tr, 270.0, 360.0, n);
        addArc(pts, x + w - br, y + h - br, br, 0.0, 90.0, n);
        addArc(pts, x + bl, y + h - bl, bl, 90.0, 180.0, n);
        return pts;
    }

    private static void addArc(List<float[]> pts, float cx, float cy, float r,
                               double a0, double a1, int n) {
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * (double) i / (double) n);
            pts.add(new float[]{(float) (cx + (double) r * Math.cos(a)),
                    (float) (cy + (double) r * Math.sin(a))});
        }
    }

    /** 4 个顶点一段（QUADS 模式；第 4 个与第 3 个重合 → 退化三角形）。 */
    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float x1, float y1,
                             float x2, float y2, int color) {
        int a = color >>> 24;
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        vc.addVertex(m, x0, y0, 0.0F).setColor(r, g, b, a).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(m, x1, y1, 0.0F).setColor(r, g, b, a).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(m, x2, y2, 0.0F).setColor(r, g, b, a).setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(m, x2, y2, 0.0F).setColor(r, g, b, a).setNormal(0.0F, 0.0F, 1.0F);
    }

    /** 凸多边形填充：以中心点为扇心。 */
    private static void fillPolygon(GuiGraphics graphics, List<float[]> pts, int color) {
        int n = pts.size();
        if (n < 3) {
            return;
        }
        float cx = 0.0F;
        float cy = 0.0F;
        for (float[] p : pts) {
            cx += p[0];
            cy += p[1];
        }
        cx /= (float) n;
        cy /= (float) n;
        Matrix4f m = graphics.pose().last().pose();
        VertexConsumer vc = graphics.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < n; i++) {
            float[] p0 = pts.get(i);
            float[] p1 = pts.get((i + 1) % n);
            quad(vc, m, cx, cy, p0[0], p0[1], p1[0], p1[1], color);
        }
    }

    /** 环形描边：外圈点与内圈点一一对应，每段一个四边形（可逐段取渐变色）。 */
    private static void strokeRing(GuiGraphics graphics, List<float[]> outer, List<float[]> inner,
                                   GradientConfig gradient, Vector4i fallback) {
        int n = Math.min(outer.size(), inner.size());
        if (n < 2) {
            return;
        }
        long time = System.currentTimeMillis();
        Matrix4f m = graphics.pose().last().pose();
        VertexConsumer vc = graphics.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < n; i++) {
            float[] o0 = outer.get(i);
            float[] o1 = outer.get((i + 1) % n);
            float[] i1 = inner.get((i + 1) % n);
            int color = gradient == null
                    ? argb(fallback)
                    : gradient.getColorAt((float) i / (float) n, time);
            quad(vc, m, o0[0], o0[1], o1[0], o1[1], i1[0], i1[1], color);
        }
    }

    public static void renderRoundedRect(GuiGraphics graphics, float x, float y, float width, float height,
                                         float topLeft, float topRight, float bottomRight, float bottomLeft,
                                         Vector4i color, int segments) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        fillPolygon(graphics, boundary(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft,
                segments(segments)), argb(color));
    }

    public static void renderRoundedRectGradient(GuiGraphics graphics, float x, float y, float width, float height,
                                                 float topLeft, float topRight, float bottomRight, float bottomLeft,
                                                 GradientConfig gradient, Vector4i fallbackColor, int segments,
                                                 RenderType renderType) {
        renderRoundedRect(graphics, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft,
                fallbackColor, segments);
    }

    public static void renderRoundedRectBorder(GuiGraphics graphics, float x, float y, float width, float height,
                                               float topLeft, float topRight, float bottomRight, float bottomLeft,
                                               float borderWidth, Vector4i color, int segments) {
        renderRoundedRectBorderGradient(graphics, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft,
                borderWidth, null, color, segments);
    }

    public static void renderRoundedRectBorderGradient(GuiGraphics graphics, float x, float y, float width, float height,
                                                       float topLeft, float topRight, float bottomRight, float bottomLeft,
                                                       float borderWidth, GradientConfig gradient, Vector4i fallbackColor,
                                                       int segments) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        float b = Math.max(1.0F, borderWidth);
        if (width <= 2.0F * b || height <= 2.0F * b) {
            graphics.fill((int) x, (int) y, (int) (x + width), (int) (y + height), argb(fallbackColor));
            return;
        }
        int n = segments(segments);
        List<float[]> outer = boundary(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, n);
        List<float[]> inner = boundary(x + b, y + b, width - 2.0F * b, height - 2.0F * b,
                Math.max(0.0F, topLeft - b), Math.max(0.0F, topRight - b),
                Math.max(0.0F, bottomRight - b), Math.max(0.0F, bottomLeft - b), n);
        strokeRing(graphics, outer, inner, gradient, fallbackColor);
    }
}
