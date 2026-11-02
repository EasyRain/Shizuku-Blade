package com.buwan.shizuku.util.tooltip;

import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/**
 * 圆角面板（SDF 着色器版）。
 *
 * <p>GUI 是按 GUI 像素网格绘制的，GUI scale 3 时 1 GUI 像素 = 3 屏幕像素，
 * 任何「按 GUI 像素排出来」的圆弧放大后都是台阶。这里在片元着色器里用有符号距离场算圆角矩形，
 * 边缘做 1 像素抗锯齿，因此圆角在任何缩放下都平滑 —— 做法参考 ModernUI 的 tooltip 着色器。</p>
 *
 * <p>关键点（踩过的坑）：写完顶点必须 {@link GuiGraphics#flush()}，否则几何不会提交（不报错也不显示）。
 * 1.21.1 的顶点接口是 {@code addVertex(...)}，已无 {@code endVertex()}。</p>
 */
public final class TooltipFrameRenderer {

    public static final RenderType TYPE = RenderType.create(
            "shizuku_tooltip_frame",
            DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(TooltipFrameRenderer::ensureShader))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false)
    );

    private static ShaderInstance shader;
    private static boolean failed;
    private static AbstractUniform uOrigin;
    private static AbstractUniform uSize;
    private static AbstractUniform uParams;
    private static AbstractUniform uFill;
    private static AbstractUniform uBorder;

    private TooltipFrameRenderer() {
    }

    private static ShaderInstance ensureShader() {
        if (shader == null && !failed) {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc == null || mc.getResourceManager() == null) {
                    return null;
                }
                shader = new ShaderInstance(mc.getResourceManager(),
                        shizuku.prefix("tooltip_frame").toString(), DefaultVertexFormat.POSITION);
                uOrigin = shader.safeGetUniform("u_Origin");
                uSize = shader.safeGetUniform("u_Size");
                uParams = shader.safeGetUniform("u_Params");
                uFill = shader.safeGetUniform("u_Fill");
                uBorder = shader.safeGetUniform("u_Border");
            } catch (Exception e) {
                failed = true;
                return null;
            }
        }
        return shader;
    }

    /**
     * @param fill   ARGB 面板底色，alpha=0 表示只画边框（宇宙刀）
     * @param border ARGB 边框色
     */
    public static void draw(GuiGraphics graphics, int x, int y, int width, int height,
                            float radius, float thickness, int fill, int border) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (ensureShader() == null) {
            if ((fill >>> 24) != 0) {
                graphics.fill(x, y, x + width, y + height, fill);
            }
            return;
        }
        uOrigin.set((float) x, (float) y, 0.0F, 0.0F);
        uSize.set(width * 0.5F, height * 0.5F, 0.0F, 0.0F);
        uParams.set(Math.min(radius, Math.min(width, height) * 0.5F), thickness, 0.0F, 0.0F);
        uFill.set(red(fill), green(fill), blue(fill), alpha(fill));
        uBorder.set(red(border), green(border), blue(border), alpha(border));

        Matrix4f matrix = graphics.pose().last().pose();
        VertexConsumer vc = graphics.bufferSource().getBuffer(TYPE);
        vc.addVertex(matrix, (float) x, (float) y, 0.0F);
        vc.addVertex(matrix, (float) x, (float) (y + height), 0.0F);
        vc.addVertex(matrix, (float) (x + width), (float) (y + height), 0.0F);
        vc.addVertex(matrix, (float) (x + width), (float) y, 0.0F);
        graphics.flush();
    }

    private static float red(int argb) {
        return (argb >> 16 & 0xFF) / 255.0F;
    }

    private static float green(int argb) {
        return (argb >> 8 & 0xFF) / 255.0F;
    }

    private static float blue(int argb) {
        return (argb & 0xFF) / 255.0F;
    }

    private static float alpha(int argb) {
        return (argb >>> 24) / 255.0F;
    }
}
