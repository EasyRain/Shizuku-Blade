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
        int alpha = alpha(strength * 0.05F);
        if (alpha <= 0) {
            return;
        }
        graphics.fill(0, 0, width, height, (alpha << 24) | TINT_RGB);
    }

    /** 暗角：上下两条竖直渐变（中心透明 → 边缘压暗）。 */
    private void renderVignette(GuiGraphicsExtractor graphics, int width, int height, float strength) {
        int edgeAlpha = alpha(strength * 0.15F);
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
    }

    /** 0.0~1.0 的强度换算成 0~255 的 alpha。 */
    private static int alpha(float intensity) {
        int value = Math.round(intensity * 255.0F);
        return Math.max(0, Math.min(255, value));
    }
}
