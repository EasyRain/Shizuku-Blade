package com.buwan.shizuku.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 26.1 阶段一简化：这个 mixin 已经被 {@link DeferredTooltipMixin} 取代，保留空壳。
 *
 * <p>1.21.1 的用途：{@code AbstractContainerScreen#renderTooltip(GuiGraphics, int, int)} 走的是
 * {@code GuiGraphics.renderTooltip(Font, List, ...)}（不带 ItemStack），所以 {@code GuiGraphicsTooltipMixin}
 * 那条路径命中不到；这里从 {@code hoveredSlot}（经 {@link AbstractContainerScreenAccessor}）取当前物品，
 * 自行绘制并取消原版渲染。</p>
 *
 * <p>26.1 的容器界面同样通过 {@code GuiGraphicsExtractor#setTooltipForNextFrame} 把 tooltip 延迟到帧末，
 * 现在统一由 {@link DeferredTooltipMixin} 在 {@code extractDeferredElements} 里接管（它自己就会读
 * {@code hoveredSlot}），两条老路径不再需要。空 mixin 合法，不会在启动时报错。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenTooltipMixin {
}
