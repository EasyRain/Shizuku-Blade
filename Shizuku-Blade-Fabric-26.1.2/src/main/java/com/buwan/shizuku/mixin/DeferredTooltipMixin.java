package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.tooltip.ShizukuTooltipRenderer;
import com.buwan.shizuku.client.tooltip.TooltipEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自定义 tooltip（26.1 最终方案）：在帧末绘制延迟 tooltip 的时刻替换绘制动作。
 *
 * <p>为什么不用 {@code setTooltipForNextFrame} 系列：26.1 里这些重载的实际调用路径不固定
 * （带 ItemStack 的、带 List 的、带 Identifier 的都试过，实测都不触发）。而
 * {@code extractDeferredElements} 是**每帧必走**的帧末出口 —— 原版 tooltip 就存在
 * {@code deferredTooltip} 字段里、由它执行。在这里判断当前悬停槽位，若是我们的刀就把该字段
 * 换成我们自己的绘制，既不依赖具体重载，也天然继承"帧末绘制、不受裁剪影响"的特性。</p>
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class DeferredTooltipMixin {

    @Inject(
            method = "extractDeferredElements",
            at = @At("HEAD")
    )
    private void shizuku$replaceDeferredTooltip(int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        Slot hovered = ((AbstractContainerScreenAccessor) screen).shizuku$getHoveredSlot();
        if (hovered == null || !hovered.hasItem()) {
            return;
        }
        ItemStack stack = hovered.getItem();
        if (!TooltipEventHandler.handles(stack)) {
            return;
        }
        GuiGraphicsExtractor self = (GuiGraphicsExtractor) (Object) this;
        Font font = mc.font;
        GuiGraphicsExtractorAccessor accessor = (GuiGraphicsExtractorAccessor) self;
        Runnable vanilla = accessor.shizuku$getDeferredTooltip();
        accessor.shizuku$setDeferredTooltip(() -> {
            boolean drawn = TooltipEventHandler.tryRender(self, stack, font, mouseX, mouseY)
                    || ShizukuTooltipRenderer.renderYmbladeTooltip(self, stack, font, mouseX, mouseY);
            if (!drawn && vanilla != null) {
                vanilla.run();
            }
        });
    }
}
