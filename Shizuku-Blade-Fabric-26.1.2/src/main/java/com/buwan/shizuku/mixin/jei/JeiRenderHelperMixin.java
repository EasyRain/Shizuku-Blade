package com.buwan.shizuku.mixin.jei;

import com.buwan.shizuku.client.tooltip.ShizukuTooltipRenderer;
import com.buwan.shizuku.client.tooltip.TooltipEventHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 让自定义 tooltip 在 JEI 里也生效（26.1 版）。
 *
 * <p>JEI 在自己这一层（{@code mezz.jei.fabric.platform.RenderHelper}）调原版渲染前手里还有
 * {@code ItemStack}，之后交给原版的那几个重载都不再带物品，所以只能在这里拦。</p>
 *
 * <p>26.1 的 Fabric 运行时使用具名（Mojmap）类名，JEI 的 jar 里也是具名引用，
 * 因此这里用 {@code remap = false} + 具名描述符直接匹配。</p>
 */
@Mixin(targets = "mezz.jei.fabric.platform.RenderHelper", remap = false)
public abstract class JeiRenderHelperMixin {

    /** 〔GuiGraphicsExtractor, List, int, int, Font, ItemStack〕 */
    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;IILnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void shizuku$customTooltip(GuiGraphicsExtractor graphics, List<?> lines, int x, int y,
                                       Font font, ItemStack stack, CallbackInfo ci) {
        if (shizuku$tryRender(graphics, stack, font, x, y)) {
            ci.cancel();
        }
    }

    /** 〔GuiGraphicsExtractor, List, int, int, Font, ItemStack, ClientTooltipPositioner〕 */
    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;IILnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void shizuku$customTooltipPositioned(GuiGraphicsExtractor graphics, List<?> lines, int x, int y,
                                                 Font font, ItemStack stack, ClientTooltipPositioner positioner,
                                                 CallbackInfo ci) {
        if (shizuku$tryRender(graphics, stack, font, x, y)) {
            ci.cancel();
        }
    }

    /**
     * 与容器路径一致：把绘制延迟到帧末（{@code deferredTooltip}），
     * 避免被 JEI 当时的 scissor 裁剪。
     */
    private static boolean shizuku$tryRender(GuiGraphicsExtractor graphics, ItemStack stack, Font font, int x, int y) {
        if (!TooltipEventHandler.handles(stack)) {
            return false;
        }
        ((com.buwan.shizuku.mixin.GuiGraphicsExtractorAccessor) graphics).shizuku$setDeferredTooltip(() -> {
            TooltipEventHandler.tryRender(graphics, stack, font, x, y);
            ShizukuTooltipRenderer.renderYmbladeTooltip(graphics, stack, font, x, y);
        });
        return true;
    }
}
