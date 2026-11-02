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
 * 它最终调用不带 ItemStack 的 {@code setTooltipForNextFrame} 重载，所以挂在带 ItemStack 重载上的
 * mixin 不会触发。这里从 {@code hoveredSlot} 取物品自行绘制。</p>
 *
 * <p><b>关键</b>：原版 tooltip 是延迟到帧末绘制的（{@code setTooltipForNextFrame} 只存一个 Runnable），
 * 因此不受容器界面残留 scissor 的影响；我们如果在 HEAD 里立即绘制，面板/边框会被容器裁剪区切掉
 * （表现为边框缺一半、四角不一致）。所以这里同样把绘制动作塞进 {@code deferredTooltip}。</p>
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
        if (!TooltipEventHandler.handles(stack)) {
            return;
        }
        ((GuiGraphicsExtractorAccessor) graphics).shizuku$setDeferredTooltip(() -> {
            TooltipEventHandler.tryRender(graphics, stack, font, mouseX, mouseY);
            ShizukuTooltipRenderer.renderYmbladeTooltip(graphics, stack, font, mouseX, mouseY);
        });
        ci.cancel();
    }
}
