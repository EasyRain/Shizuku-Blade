package com.buwan.shizuku.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/**
 * 时停的屏幕后处理：整体去饱和 + 轻微暗角。
 *
 * <p>Fabric 版：每帧由 {@code HudRenderCallback} 调 {@link #renderOverlay(GuiGraphics)}，
 * 每 tick 由 {@code ClientTickEvents.END_CLIENT_TICK} 调 {@link #tick()}。
 * Forge 的雾色事件在 Fabric 没有等价回调，这里不再改雾色（只保留屏幕叠加层）。</p>
 *
 * <p>1.21 的顶点 API：{@code Tesselator#begin} 直接返回 builder，
 * 顶点用 {@code addVertex(...).setColor(...).setUv(...)}，最后自己
 * {@code BufferUploader.drawWithShader(buildOrThrow())} —— HUD 阶段
 * {@code graphics.bufferSource()} 不会自动 flush，交给它画会直接丢。</p>
 */
public class TimeStopScreenEffect {
    private static final TimeStopScreenEffect INSTANCE = new TimeStopScreenEffect();

    public static TimeStopScreenEffect getInstance() {
        return INSTANCE;
    }

    public void tick() {
        TimeStopShaderManager.updateStrength();
    }

    public void renderOverlay(net.minecraft.client.gui.GuiGraphics graphics) {
        if (TimeStopShaderManager.isActive()) {
            float strength = TimeStopShaderManager.getCurrentStrength();
            if (!(strength < 0.001F)) {
                Minecraft mc = Minecraft.getInstance();
                int width = mc.getWindow().getGuiScaledWidth();
                int height = mc.getWindow().getGuiScaledHeight();
                this.renderColorOverlay(width, height, strength);
                this.renderVignette(width, height, strength);
            }
        }
    }

    private void renderColorOverlay(int width, int height, float strength) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float alpha = strength * 0.05F;
        float r = 1.0F;
        float g = 0.84F;
        float b = 0.0F;
        buffer.addVertex(0.0F, (float) height, -90.0F).setColor(r, g, b, alpha).setUv(0, 0);
        buffer.addVertex((float) width, (float) height, -90.0F).setColor(r, g, b, alpha).setUv(0, 0);
        buffer.addVertex((float) width, 0.0F, -90.0F).setColor(r, g, b, alpha).setUv(0, 0);
        buffer.addVertex(0.0F, 0.0F, -90.0F).setColor(r, g, b, alpha).setUv(0, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void renderVignette(int width, int height, float strength) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float edgeAlpha = strength * 0.15F;
        float centerAlpha = 0.0F;
        float r = 0.8F;
        float g = 0.6F;
        float b = 0.0F;
        int edgeSize = (int) ((float) Math.min(width, height) * 0.3F);
        buffer.addVertex(0.0F, 0.0F, -90.0F).setColor(r, g, b, edgeAlpha).setUv(0, 0);
        buffer.addVertex((float) width, 0.0F, -90.0F).setColor(r, g, b, edgeAlpha).setUv(0, 0);
        buffer.addVertex((float) width, (float) edgeSize, -90.0F).setColor(r, g, b, centerAlpha).setUv(0, 0);
        buffer.addVertex(0.0F, (float) edgeSize, -90.0F).setColor(r, g, b, centerAlpha).setUv(0, 0);
        buffer.addVertex(0.0F, (float) (height - edgeSize), -90.0F).setColor(r, g, b, centerAlpha).setUv(0, 0);
        buffer.addVertex((float) width, (float) (height - edgeSize), -90.0F).setColor(r, g, b, centerAlpha).setUv(0, 0);
        buffer.addVertex((float) width, (float) height, -90.0F).setColor(r, g, b, edgeAlpha).setUv(0, 0);
        buffer.addVertex(0.0F, (float) height, -90.0F).setColor(r, g, b, edgeAlpha).setUv(0, 0);
        buffer.addVertex(0.0F, (float) edgeSize, -90.0F).setColor(r, g, b, edgeAlpha).setUv(0, 0);
        buffer.addVertex((float) edgeSize, (float) edgeSize, -90.0F).setColor(r, g, b, centerAlpha).setUv(0, 0);
        buffer.addVertex((float) edgeSize, (float) (height - edgeSize), -90.0F).setColor(r, g, b, centerAlpha).setUv(0, 0);
        buffer.addVertex(0.0F, (float) (height - edgeSize), -90.0F).setColor(r, g, b, edgeAlpha).setUv(0, 0);
        buffer.addVertex((float) (width - edgeSize), (float) edgeSize, -90.0F).setColor(r, g, b, centerAlpha).setUv(0, 0);
        buffer.addVertex((float) width, (float) edgeSize, -90.0F).setColor(r, g, b, edgeAlpha).setUv(0, 0);
        buffer.addVertex((float) width, (float) (height - edgeSize), -90.0F).setColor(r, g, b, edgeAlpha).setUv(0, 0);
        buffer.addVertex((float) (width - edgeSize), (float) (height - edgeSize), -90.0F).setColor(r, g, b, centerAlpha).setUv(0, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
