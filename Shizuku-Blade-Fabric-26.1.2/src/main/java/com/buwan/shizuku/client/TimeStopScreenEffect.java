package com.buwan.shizuku.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 时停的屏幕后处理：整体去饱和 + 轻微暗角。
 *
 * <p>Fabric 版：每帧由 {@code HudRenderCallback} 调 {@link #renderOverlay(GuiGraphicsExtractor)}，
 * 每 tick 由 {@code ClientTickEvents.END_CLIENT_TICK} 调 {@link #tick()}。
 * Forge 的雾色事件在 Fabric 没有等价回调，这里不再改雾色（只保留屏幕叠加层）。</p>
 *
 * <p>26.1 阶段一简化：原来用 {@code Tesselator} + {@code BufferUploader.drawWithShader}
 * 自己提交一批 {@code POSITION_COLOR} 顶点（HUD 阶段 {@code bufferSource} 不会自动 flush，
 * 只能自己画）。26.1 删掉了这条 GUI 直绘通道，改为纯 {@code fill} / {@code fillGradient} 两层：
 * 一层黄色去饱和覆盖 + 上下两条竖直渐变做暗角（左右两条竖向条舍弃 —— {@code fillGradient}
 * 只支持竖直渐变，用纯色条会看出明显硬边，宁可不画）。</p>
 */
public class TimeStopScreenEffect {
    private static final TimeStopScreenEffect INSTANCE = new TimeStopScreenEffect();

    /** 黄色压暗色（r=1.0, g=0.84, b=0.0，与 1.21.1 的 overlay 顶点色一致）。 */
    private static final int TINT_RGB = 0xFFD600;

    /** 暗角色（r=0.8, g=0.6, b=0.0）。 */
    private static final int VIGNETTE_RGB = 0xCC9900;


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

    public void renderOverlay(GuiGraphicsExtractor graphics) {
        if (TimeStopShaderManager.isActive()) {
            float strength = TimeStopShaderManager.getCurrentStrength();
            if (!(strength < 0.001F)) {
                int width = graphics.guiWidth();
                int height = graphics.guiHeight();
                this.renderColorOverlay(graphics, width, height, strength);
                this.renderVignette(graphics, width, height, strength);
            }
        }
    }

    /** 整体黄色去饱和覆盖（原 POSITION_COLOR 四边形 → 一次全屏 fill）。 */
    private void renderColorOverlay(GuiGraphicsExtractor graphics, int width, int height, float strength) {
        // 原版一帧叠加 26 次，这里折算成等效 alpha（见 OVERLAY_PASSES 注释）
        int alpha = alpha(accumulated(strength * 0.05F));
        if (alpha <= 0) {
            return;
        }
        graphics.fill(0, 0, width, height, (alpha << 24) | TINT_RGB);
    }

    /** 暗角：上下两条竖直渐变（中心透明 → 边缘压暗）。 */
    private void renderVignette(GuiGraphicsExtractor graphics, int width, int height, float strength) {
        int edgeAlpha = alpha(accumulated(strength * 0.15F));
        if (edgeAlpha <= 0) {
            return;
        }
        int edgeColor = (edgeAlpha << 24) | VIGNETTE_RGB;
        int clear = VIGNETTE_RGB;   // alpha=0
        int edgeSize = (int) (Math.min(width, height) * 0.3F);
        if (edgeSize <= 0) {
            return;
        }
        graphics.fillGradient(0, 0, width, edgeSize, edgeColor, clear);
        graphics.fillGradient(0, height - edgeSize, width, height, clear, edgeColor);

        // 左右两条：原版是水平渐变四边形，而 26.1 的 fillGradient 只支持竖直方向
        // ⇒ 用阶梯色柱逼近（每柱内 alpha 恒定，视觉上与渐变等价）
        float accEdge = accumulated(strength * 0.15F);
        int steps = 16;
        for (int i = 0; i < steps; i++) {
            int a = alpha(accEdge * (1.0F - (float) i / steps));
            if (a <= 0) {
                continue;
            }
            int color = (a << 24) | VIGNETTE_RGB;
            int x0 = edgeSize * i / steps;
            int x1 = Math.max(x0 + 1, edgeSize * (i + 1) / steps);
            graphics.fill(x0, edgeSize, x1, height - edgeSize, color);
            graphics.fill(width - x1, edgeSize, width - x0, height - edgeSize, color);
        }
    }

    /** 0.0~1.0 的强度换算成 0~255 的 alpha。 */
    private static int alpha(float intensity) {
        int value = Math.round(intensity * 255.0F);
        return Math.max(0, Math.min(255, value));
    }
}
