package com.buwan.shizuku.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/**
 * 时停的屏幕后处理：整体去饱和 + 轻微暗角。
 *
 * <p>Fabric 版：每帧由 {@code HudRenderCallback} 调 {@link #renderOverlay()}，
 * 每 tick 由 {@code ClientTickEvents.END_CLIENT_TICK} 调 {@link #tick()}。
 * Forge 的雾色事件在 Fabric 没有等价回调，这里不再改雾色（只保留屏幕叠加层）。</p>
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
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        float alpha = strength * 0.05F;
        float r = 1.0F;
        float g = 0.84F;
        float b = 0.0F;
        buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(0.0, height, -90.0).color(r, g, b, alpha).endVertex();
        buffer.vertex(width, height, -90.0).color(r, g, b, alpha).endVertex();
        buffer.vertex(width, 0.0, -90.0).color(r, g, b, alpha).endVertex();
        buffer.vertex(0.0, 0.0, -90.0).color(r, g, b, alpha).endVertex();
        tesselator.end();
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
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        float edgeAlpha = strength * 0.15F;
        float centerAlpha = 0.0F;
        float r = 0.8F;
        float g = 0.6F;
        float b = 0.0F;
        int edgeSize = (int)(Math.min(width, height) * 0.3F);
        buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(0.0, 0.0, -90.0).color(r, g, b, edgeAlpha).endVertex();
        buffer.vertex(width, 0.0, -90.0).color(r, g, b, edgeAlpha).endVertex();
        buffer.vertex(width, edgeSize, -90.0).color(r, g, b, centerAlpha).endVertex();
        buffer.vertex(0.0, edgeSize, -90.0).color(r, g, b, centerAlpha).endVertex();
        buffer.vertex(0.0, height - edgeSize, -90.0).color(r, g, b, centerAlpha).endVertex();
        buffer.vertex(width, height - edgeSize, -90.0).color(r, g, b, centerAlpha).endVertex();
        buffer.vertex(width, height, -90.0).color(r, g, b, edgeAlpha).endVertex();
        buffer.vertex(0.0, height, -90.0).color(r, g, b, edgeAlpha).endVertex();
        buffer.vertex(0.0, edgeSize, -90.0).color(r, g, b, edgeAlpha).endVertex();
        buffer.vertex(edgeSize, edgeSize, -90.0).color(r, g, b, centerAlpha).endVertex();
        buffer.vertex(edgeSize, height - edgeSize, -90.0).color(r, g, b, centerAlpha).endVertex();
        buffer.vertex(0.0, height - edgeSize, -90.0).color(r, g, b, edgeAlpha).endVertex();
        buffer.vertex(width - edgeSize, edgeSize, -90.0).color(r, g, b, centerAlpha).endVertex();
        buffer.vertex(width, edgeSize, -90.0).color(r, g, b, edgeAlpha).endVertex();
        buffer.vertex(width, height - edgeSize, -90.0).color(r, g, b, edgeAlpha).endVertex();
        buffer.vertex(width - edgeSize, height - edgeSize, -90.0).color(r, g, b, centerAlpha).endVertex();
        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
