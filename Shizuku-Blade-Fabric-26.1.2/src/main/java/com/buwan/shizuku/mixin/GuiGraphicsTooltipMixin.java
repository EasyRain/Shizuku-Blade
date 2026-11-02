package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.tooltip.ShizukuTooltipRenderer;
import com.buwan.shizuku.client.tooltip.TooltipEventHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自定义 tooltip：Fabric 没有可取消的 RenderTooltipEvent，
 * 这里在 {@code GuiGraphics.renderTooltip(Font, ItemStack, int, int)} HEAD 拦截，
 * 由 {@link TooltipEventHandler} / {@link ShizukuTooltipRenderer} 自行绘制并取消原版渲染。
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsTooltipMixin {

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void shizuku$customTooltip(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        GuiGraphics self = (GuiGraphics) (Object) this;
        if (TooltipEventHandler.tryRender(self, stack, font, x, y)
                || ShizukuTooltipRenderer.renderYmbladeTooltip(self, stack, font, x, y)) {
            ci.cancel();
        }
    }
}
