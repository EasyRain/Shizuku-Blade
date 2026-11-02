package com.buwan.shizuku.util.tooltip;

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
 * <p>为什么用着色器：GUI 是按 GUI 像素网格绘制的，GUI scale 3 时 1 GUI 像素 = 3 屏幕像素，
 * 任何「按 GUI 像素排出来」的圆弧（扫描线 / 像素贴图）放大后都是台阶。
 * 这里改为在片元着色器里用有符号距离场算圆角矩形，边缘做 1 像素抗锯齿，
 * 因此圆角在任何缩放下都是平滑的 —— 做法参考 ModernUI 的 tooltip 着色器。</p>
 *
 * <p>关键点（踩过的坑）：</p>
 * <ul>
 *   <li>写完顶点必须调用 {@link GuiGraphics#flush()}，否则这批几何不会被提交（不报错、什么都不显示）；</li>
 *   <li>{@code RenderType} 必须挂自己的着色器，用自己的 {@code ShaderInstance}；</li>
 *   <li>1.20.1 的 {@code BufferBuilder} 每个顶点都要 {@code endVertex()}。</li>
 * </ul>
 */
public final class TooltipFrameRenderer {

    /** 与 TooltipFrameRenderer 配套的顶点格式：只需要位置。 */
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
                shader = new ShaderInstance(mc.getResourceManager(), "tooltip_frame",
                        DefaultVertexFormat.POSITION);
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
            // 着色器不可用时的兜底：至少保证面板可见
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
        vc.vertex(matrix, (float) x, (float) y, 0.0F).endVertex();
        vc.vertex(matrix, (float) x, (float) (y + height), 0.0F).endVertex();
        vc.vertex(matrix, (float) (x + width), (float) (y + height), 0.0F).endVertex();
        vc.vertex(matrix, (float) (x + width), (float) y, 0.0F).endVertex();
        // 必须手动刷新，否则这批几何不会被提交
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
