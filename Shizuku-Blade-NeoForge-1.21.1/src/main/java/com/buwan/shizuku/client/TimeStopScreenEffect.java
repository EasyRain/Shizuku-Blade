package com.buwan.shizuku.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@OnlyIn(Dist.CLIENT)
public class TimeStopScreenEffect {
    private static final TimeStopScreenEffect INSTANCE = new TimeStopScreenEffect();

    public static TimeStopScreenEffect getInstance() {
        return INSTANCE;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        TimeStopShaderManager.updateStrength();
    }

    @SubscribeEvent
    public void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (TimeStopShaderManager.isActive()) {
            float[] tint = TimeStopShaderManager.getTimeStopTint();
            float strength = TimeStopShaderManager.getCurrentStrength();
            float r = (float) ((double) event.getRed() * (1.0 - (double) strength * 0.1) + (double) (tint[0] * strength) * 0.1);
            float g = (float) ((double) event.getGreen() * (1.0 - (double) strength * 0.1) + (double) (tint[1] * strength) * 0.1);
            float b = (float) ((double) event.getBlue() * (1.0 - (double) strength * 0.1) + (double) (tint[2] * strength) * 0.1);
            event.setRed(r);
            event.setGreen(g);
            event.setBlue(b);
        }
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
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
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
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
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
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
