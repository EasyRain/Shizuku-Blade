package com.buwan.shizuku.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre;
import net.minecraftforge.client.event.ViewportEvent.ComputeFogColor;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 时停的屏幕后处理：雾色变冷、整体去饱和 + 轻微暗角。
 *
 * 订阅 ComputeFogColor 改雾色，并在 RenderGameOverlay 的 Pre 阶段叠加颜色层。
 */
@OnlyIn(Dist.CLIENT)
public class TimeStopScreenEffect {
    private static final TimeStopScreenEffect INSTANCE = new TimeStopScreenEffect();

    public static TimeStopScreenEffect getInstance() {
        return INSTANCE;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase == Phase.END) {
            TimeStopShaderManager.updateStrength();
        }
    }

    @SubscribeEvent
    public void onFogColor(ComputeFogColor event) {
        if (TimeStopShaderManager.isActive()) {
            float[] tint = TimeStopShaderManager.getTimeStopTint();
            float strength = TimeStopShaderManager.getCurrentStrength();
            float r = (float)(event.getRed() * (1.0 - strength * 0.1) + tint[0] * strength * 0.1);
            float g = (float)(event.getGreen() * (1.0 - strength * 0.1) + tint[1] * strength * 0.1);
            float b = (float)(event.getBlue() * (1.0 - strength * 0.1) + tint[2] * strength * 0.1);
            event.setRed(r);
            event.setGreen(g);
            event.setBlue(b);
        }
    }

    @SubscribeEvent
    public void onRenderGameOverlay(Pre event) {
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
