package com.buwan.shizuku.mixin;

import com.buwan.shizuku.sa.UnleashedJudgment;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 无铭「有名」不可损坏或已耗尽耐久时，跳过扣耐久。
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("shizuku/itemstackmixin");
    @Inject(method = "hurtAndBreak", at = @At("HEAD"), cancellable = true, require = 0)
    private void onHurtAndBreak(int amount, LivingEntity entity, Consumer<LivingEntity> onBroken, CallbackInfo ci) {
        ItemStack stack = (ItemStack)(Object)this;
        if (!UnleashedJudgment.isBlade(stack)) {
            return;
        }

        try {
            if (!stack.isDamageableItem()) {
                ci.cancel();
                return;
            }

            if (stack.getDamageValue() >= stack.getMaxDamage()) {
                ci.cancel();
                return;
            }
        } catch (IllegalArgumentException var7) {
            LOGGER.debug("unexpected error", var7);
            ci.cancel();
        }
    }
}
