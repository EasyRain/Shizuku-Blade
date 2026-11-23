package com.buwan.shizuku.util.tooltip;

import com.buwan.shizuku.client.tooltip.DynamicRoundedPanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * 圆角面板（26.1 阶段一：纹理化实现）。
 *
 * <p>1.20.1/1.21.1 的版本在片元着色器里用有符号距离场算圆角矩形（{@code tooltip_frame} 着色器），
 * 边缘 1 像素抗锯齿，任何 GUI 缩放下都平滑。</p>
 *
 * <p>26.1 把 {@code ShaderInstance} 整个删了，GUI 的 {@code fill} 又只有整像素，
 * 所以这里改用现成的 {@link DynamicRoundedPanel}：它在运行时用 SDF + 覆盖率算出一张圆角面板贴图
 * （自带抗锯齿与缓存），再按 1:1 {@code blit} 出来 —— 视觉上等价于原来的着色器版，
 * 且不需要碰新的 RenderPipeline。</p>
 *
 * <p>失败兜底：贴图生成/绘制出任何问题都静默降级成 {@code fill}（有底色时），绝不抛异常。</p>
 */
public final class TooltipFrameRenderer {

    private TooltipFrameRenderer() {
    }

    /**
     * @param fill   ARGB 面板底色，alpha=0 表示只画边框（宇宙刀）
     * @param border ARGB 边框色
     */
    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            float radius, float thickness, int fill, int border) {
        if (width <= 0 || height <= 0) {
            return;
        }
        try {
            Identifier panel = DynamicRoundedPanel.get(width, height, radius, thickness, fill, List.of(border));
            int[] texSize = DynamicRoundedPanel.getTexSize(width, height);
            graphics.blit(RenderPipelines.GUI_TEXTURED, panel,
                    x, y, 0.0F, 0.0F, width, height,
                    texSize[0], texSize[1], texSize[0], texSize[1]);
        } catch (Throwable failed) {
            // 26.1 阶段一简化：贴图不可用时只保留纯色底（边框放弃），不影响调用方流程。
            if ((fill >>> 24) != 0) {
                graphics.fill(x, y, x + width, y + height, fill);
            }
        }
    }
}
