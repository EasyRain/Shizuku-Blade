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


    /**
     * 暗角是否启用：<b>关</b>（用户 2026-11-24 决定，代码保留但不用）。
     *
     * <p>理由：① 原版的暗角其实也没生效 —— 它与颜色层同挂 {@code RenderGuiOverlayEvent.Pre}，
     * 四条渐变四边形的边缘 alpha 累加 26 次≈99%、中心为 0，观感完全被颜色层盖住；
     * ② 我们按等效 alpha 单次绘制后，它的水平渐变只能靠阶梯色柱逼近，画得不规范
     * （26.1 端尤其明显）。</p>
     *
     * <p>想开回来把常量改成 {@code true} 即可（{@link #renderVignette} 原样保留）。</p>
     */
    private static final boolean VIGNETTE_ENABLED = false;

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
                if (VIGNETTE_ENABLED) {
                    this.renderVignette(width, height, strength);
                }
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
        // 原版一帧叠加 26 次，这里折算成等效 alpha（见 OVERLAY_PASSES 注释）
        float alpha = accumulated(strength * 0.05F);
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
        float edgeAlpha = accumulated(strength * 0.15F);
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
