package com.buwan.shizuku.mixin;

import net.minecraft.server.level.ServerLevel;
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
     * 1.20.1 目标签名为 {@code hurtAndBreak(int, LivingEntity, Consumer<LivingEntity>)}；
     * 1.21.1 已改为 {@code hurtAndBreak(int, ServerLevel, @Nullable LivingEntity, Consumer<Item>)}，
     * 且存在 ServerPlayer 与 LivingEntity 两个 4 参重载，必须用完整描述符显式指定，
     * 否则 Mixin 按名字匹配到 ServerPlayer 重载会导致描述符不匹配而崩溃。
     */
    @Inject(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void onHurtAndBreak(int amount, ServerLevel level, LivingEntity entity, Consumer<Item> onBreak,
                                CallbackInfo ci) {
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
