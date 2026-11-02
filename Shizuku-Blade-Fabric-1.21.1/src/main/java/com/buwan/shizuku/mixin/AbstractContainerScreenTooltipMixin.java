package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.tooltip.ShizukuTooltipRenderer;
import com.buwan.shizuku.client.tooltip.TooltipEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
 * <p>1.20.1 的 {@code AbstractContainerScreen#renderTooltip(GuiGraphics, int, int)}
 * 走的是 {@code GuiGraphics.renderTooltip(Font, List, Optional, int, int)}——不传 ItemStack，
 * 所以在 {@code GuiGraphics.renderTooltip(Font, ItemStack, int, int)} 上的 mixin 不会命中。
 * 这里从 {@code hoveredSlot}（通过 {@link AbstractContainerScreenAccessor}）取当前物品，
 * 自行绘制并取消原版渲染。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenTooltipMixin {

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void shizuku$containerCustomTooltip(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
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
