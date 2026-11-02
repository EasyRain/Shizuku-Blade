package com.buwan.shizuku.mixin.jei;

import com.buwan.shizuku.client.tooltip.ShizukuTooltipRenderer;
import com.buwan.shizuku.client.tooltip.TooltipEventHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 让自定义 tooltip 在 JEI 里也生效。
 *
 * <p>Fabric 版 JEI 在 {@code JeiTooltip → RenderHelper} 这一层还持有 {@code ItemStack}，
 * 但最终调用的是原版「只有文本行」的 {@code GuiGraphics.renderTooltip} 重载，那里拿不到物品，
 * 所以我们的 mixin 拦不到。这里直接在 JEI 的 RenderHelper 上拦截 —— 它在调用原版渲染前
 * 手里是有 {@code ItemStack} 的。</p>
 *
 * <p>Forge 上原版 mod 之所以在 JEI 里也显示自定义 tooltip，是因为 Forge 给
 * {@code GuiGraphics} 的那个重载补了 {@code Optional<ItemStack>}，其事件天然能识别物品；
 * Fabric 没有这个补丁，只能这样补。</p>
 *
 * <p>目标类是 mod 类（JEI 编译在 intermediary 命名空间下），因此 {@code remap = false}
 * 并用 intermediary 描述符定位方法。</p>
 */
@Mixin(targets = "mezz.jei.fabric.platform.RenderHelper", remap = false)
public abstract class JeiRenderHelperMixin {

    /** 〔GuiGraphics, List, int, int, Font, ItemStack〕 */
    @Inject(
            method = "renderTooltip(Lnet/minecraft/class_332;Ljava/util/List;IILnet/minecraft/class_327;Lnet/minecraft/class_1799;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void shizuku$customTooltip(GuiGraphics graphics, List<?> lines, int x, int y,
                                       Font font, ItemStack stack, CallbackInfo ci) {
        if (shizuku$tryRender(graphics, stack, font, x, y)) {
            ci.cancel();
        }
    }

    /** 〔GuiGraphics, List, int, int, Font, ItemStack, ClientTooltipPositioner〕 */
    @Inject(
            method = "renderTooltip(Lnet/minecraft/class_332;Ljava/util/List;IILnet/minecraft/class_327;Lnet/minecraft/class_1799;Lnet/minecraft/class_8000;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void shizuku$customTooltipPositioned(GuiGraphics graphics, List<?> lines, int x, int y,
                                                 Font font, ItemStack stack, ClientTooltipPositioner positioner,
                                                 CallbackInfo ci) {
        if (shizuku$tryRender(graphics, stack, font, x, y)) {
            ci.cancel();
        }
    }

    /**
     * 与物品栏里的 mixin 走同一套判定，两条入口缺一不可：
     * <ul>
     *   <li>{@code tryRender} —— 星河刀（mosmicblade）等实现 {@code ICustomTooltip} 的物品；</li>
     *   <li>{@code renderYmbladeTooltip} —— 无铭「有名」（ymblade）。</li>
     * </ul>
     */
    private static boolean shizuku$tryRender(GuiGraphics graphics, ItemStack stack, Font font, int x, int y) {
        return TooltipEventHandler.tryRender(graphics, stack, font, x, y)
                || ShizukuTooltipRenderer.renderYmbladeTooltip(graphics, stack, font, x, y);
    }
}
