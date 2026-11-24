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


    /**
     * 原版（Forge）把颜色层挂在 {@code RenderGuiOverlayEvent.Pre} 上，而该事件是
     * <b>按 GUI 图层逐个触发</b>的：Forge 的 {@code GuiOverlayManager} 会注册
     * {@code VanillaGuiOverlay.values()} 的<b>全部 26 个</b>图层，且在触发 {@code Pre}
     * 之前<b>不做任何谓词过滤</b>（谓词在各图层自己的 render 里）⇒ 原版实际是
     * <b>同一层每帧叠加 26 次</b>：{@code alpha = strength * 0.05} 累加 26 次 ≈ <b>0.74</b>
     * —— 这才是原版那个明显「黄色全屏滤镜」的真正来源（单次 5% 几乎看不见）。
     *
     * <p>Fabric / NeoForge 的 HUD 回调每帧只触发一次，于是这里按累积公式折算成等效 alpha：
     * {@code a_eff = 1 - (1 - a)^N}（对同一颜色重复 alpha 混合 N 次与该式等价，
     * 逐像素结果与原版一致）。</p>
     *
     * <p><b>取值：原版 26 次，我们取 13（用户 2026-11-24 决定减半）。</b>
     * 原版满强度下黄色层约 74%、暗角约 99%，观感「特别黄」；减半后约 49% / 88%，
     * 保留明显的时停滤镜感又不至于糊住画面。想更接近原版就改回 26，想更淡就再减。</p>
     */
    private static final int OVERLAY_PASSES = 13;

    /** 把「单次 alpha」折算成「叠加 {@link #OVERLAY_PASSES} 次后的等效 alpha」。 */
    private static float accumulated(float base) {
        return 1.0F - (float) Math.pow(1.0F - base, OVERLAY_PASSES);
    }

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
        // 原版一帧叠加 26 次，这里折算成等效 alpha（见 OVERLAY_PASSES 注释）
        float alpha = accumulated(strength * 0.05F);
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
        float edgeAlpha = accumulated(strength * 0.15F);
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
