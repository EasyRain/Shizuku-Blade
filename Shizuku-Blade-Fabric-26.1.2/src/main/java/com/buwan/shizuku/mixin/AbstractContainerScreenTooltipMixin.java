package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.tooltip.ShizukuTooltipRenderer;
import com.buwan.shizuku.client.tooltip.TooltipEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自定义 tooltip（容器界面路径）。
 *
 * <p>26.1 的容器界面走 {@code AbstractContainerScreen#extractTooltip(GuiGraphicsExtractor, int, int)}，
 * 它最终调用 {@code setTooltipForNextFrame(Font, List, Optional, ...)} —— 那里已经没有 ItemStack，
 * 所以挂在 {@code setTooltipForNextFrame(Font, ItemStack, int, int)} 上的 mixin 永远不会触发
 * （该方法在 26.1 里没有调用者）。这里从 {@code hoveredSlot} 取当前物品，自行绘制并取消原版渲染。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenTooltipMixin {

    @Inject(
            method = "extractTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void shizuku$containerCustomTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                CallbackInfo ci) {
        Slot hovered = ((AbstractContainerScreenAccessor) this).shizuku$getHoveredSlot();
        if (hovered == null || !hovered.hasItem()) {
            return;
        }
        ItemStack stack = hovered.getItem();
        var font = Minecraft.getInstance().font;
        if (TooltipEventHandler.tryRender(graphics, stack, font, mouseX, mouseY)
                || ShizukuTooltipRenderer.renderYmbladeTooltip(graphics, stack, font, mouseX, mouseY)) {
            ci.cancel();
        }
    }
}
