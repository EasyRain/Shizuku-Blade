package com.buwan.shizuku.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    /**
     * 1.20.1 是 3 参版本 {@code hurtAndBreak(int, LivingEntity, Consumer)}；
     * 4 参（带 ServerLevel）是 1.21.1 才有的，这里必须用 1.20.1 的描述符。
     */
    @Inject(
            method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void onHurtAndBreak(int amount, LivingEntity entity, Consumer<Item> onBreak, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;

        try {
            if (!stack.isDamageableItem()) {
                ci.cancel();
                return;
            }

            if (stack.getDamageValue() >= stack.getMaxDamage()) {
                ci.cancel();
                return;
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            ci.cancel();
        }
    }
}
