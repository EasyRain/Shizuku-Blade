package com.buwan.shizuku.util.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector4i;

/**
 * 圆角矩形绘制（1.21 版）。
 *
 * <p><b>为什么不用 {@code graphics.bufferSource().getBuffer(RenderType.gui())}：</b>
 * {@code RenderType.gui()} 的顶点模式是 <b>QUADS</b>（每 4 个顶点一个四边形），
 * 而圆角扇/环天然是三角形（每 3 个顶点）。把三角形顶点塞进 QUADS 会被解析错位，
 * 表现就是「底色缺块 + 一条斜的横条」（1.21 端口曾经的老 bug）。</p>
 *
 * <p>所以这里照原版 Forge 的做法：**自建 BufferBuilder 并显式声明 {@code Mode.TRIANGLES}**，
 * 用 {@code position_color} shader 直接上传绘制。顶点数与模式严格一致，几何不会错位。</p>
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

    /** 圆角矩形的边界点（顺时针，从左上角圆弧起点开始）。 */
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

    private static void vertex(BufferBuilder b, Matrix4f m, float x, float y, int argb) {
        b.addVertex(m, x, y, 0.0F).setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
    }

    /** 自建 TRIANGLES 缓冲区并立即绘制（与 Forge 端实现一致）。 */
    private static void draw(GuiGraphics graphics, int color, java.util.function.Consumer<BufferBuilder> emitter) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        emitter.accept(builder);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /** 凸多边形填充：以中心点为扇心，每个三角形用 3 个顶点。 */
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
        final float fcx = cx;
        final float fcy = cy;
        draw(graphics, color, b -> {
            Matrix4f m = graphics.pose().last().pose();
            for (int i = 0; i < n; i++) {
                float[] p0 = pts.get(i);
                float[] p1 = pts.get((i + 1) % n);
                vertex(b, m, fcx, fcy, color);
                vertex(b, m, p0[0], p0[1], color);
                vertex(b, m, p1[0], p1[1], color);
            }
        });
    }

    /** 环形描边：外圈点与内圈点一一对应，每段两个三角形。 */
    private static void strokeRing(GuiGraphics graphics, List<float[]> outer, List<float[]> inner, int color) {
        int n = Math.min(outer.size(), inner.size());
        if (n < 2) {
            return;
        }
        draw(graphics, color, b -> {
            Matrix4f m = graphics.pose().last().pose();
            for (int i = 0; i < n; i++) {
                float[] o0 = outer.get(i);
                float[] o1 = outer.get((i + 1) % n);
                float[] i0 = inner.get(i);
                float[] i1 = inner.get((i + 1) % n);
                vertex(b, m, o0[0], o0[1], color);
                vertex(b, m, o1[0], o1[1], color);
                vertex(b, m, i1[0], i1[1], color);
                vertex(b, m, o0[0], o0[1], color);
                vertex(b, m, i1[0], i1[1], color);
                vertex(b, m, i0[0], i0[1], color);
            }
        });
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
        // 渐变版本暂用纯色（与旧实现行为一致：回退到 fallbackColor）
        renderRoundedRect(graphics, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft,
                fallbackColor, segments);
    }

    public static void renderRoundedRectBorder(GuiGraphics graphics, float x, float y, float width, float height,
                                               float topLeft, float topRight, float bottomRight, float bottomLeft,
                                               float borderWidth, Vector4i color, int segments) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        float b = Math.max(1.0F, borderWidth);
        if (width <= 2.0F * b || height <= 2.0F * b) {
            graphics.fill((int) x, (int) y, (int) (x + width), (int) (y + height), argb(color));
            return;
        }
        int n = segments(segments);
        List<float[]> outer = boundary(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, n);
        List<float[]> inner = boundary(x + b, y + b, width - 2.0F * b, height - 2.0F * b,
                Math.max(0.0F, topLeft - b), Math.max(0.0F, topRight - b),
                Math.max(0.0F, bottomRight - b), Math.max(0.0F, bottomLeft - b), n);
        strokeRing(graphics, outer, inner, argb(color));
    }

    public static void renderRoundedRectBorderGradient(GuiGraphics graphics, float x, float y, float width, float height,
                                                       float topLeft, float topRight, float bottomRight, float bottomLeft,
                                                       float borderWidth, GradientConfig gradient, Vector4i fallbackColor,
                                                       int segments) {
        renderRoundedRectBorder(graphics, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft,
                borderWidth, fallbackColor, segments);
    }
}
