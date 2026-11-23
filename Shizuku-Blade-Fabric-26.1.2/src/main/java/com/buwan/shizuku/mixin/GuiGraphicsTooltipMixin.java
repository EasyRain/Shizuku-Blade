package com.buwan.shizuku.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 26.1 阶段一简化：这个 mixin 已经被 {@link DeferredTooltipMixin} 取代，保留空壳。
 *
 * <p>1.21.1 的用途：{@code GuiGraphics#renderTooltip(Font, ItemStack, int, int)} 的 HEAD 拦截，
 * 由 {@code TooltipEventHandler} / {@code ShizukuTooltipRenderer} 自行绘制并取消原版渲染。</p>
 *
 * <p>26.1 里 {@code GuiGraphics} 换成了 {@code GuiGraphicsExtractor}，而 tooltip 是
 * <b>延迟到帧末</b>绘制的（{@code setTooltipForNextFrame} 只是把绘制动作存进
 * {@code deferredTooltip} 字段，真正执行在 {@code extractDeferredElements}）。所以挂在
 * {@code setTooltipForNextFrame} 上既不可靠、也不该立刻画（会被容器界面残留的 scissor 切掉）。
 * 现方案由 {@link DeferredTooltipMixin} 在 {@code extractDeferredElements} 里替换那个字段完成，
 * 本类不再注入任何东西，避免同一帧画两遍 tooltip。</p>
 *
 * <p>保留空 mixin 是因为 {@code shizuku.mixins.json} 的 client 段还列着它；
 * 空 mixin 合法，没有 injector 就不会解析目标方法，启动不会报错。</p>
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsTooltipMixin {
}
