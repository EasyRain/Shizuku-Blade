package com.buwan.shizuku.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
     * 1.20.1 目标签名为 {@code hurtAndBreak(int, LivingEntity, Consumer<LivingEntity>)}。
     *
     * <p>1.21.1 **原版**只有两个重载：{@code hurtAndBreak(int, LivingEntity, EquipmentSlot)} 和
     * {@code hurtAndBreak(int, ServerLevel, ServerPlayer, Consumer<Item>)} —— 注意第三个参数是
     * {@code ServerPlayer} 而不是 {@code LivingEntity}（NeoForge 打了补丁改成 LivingEntity，
     * 所以 NeoForge 端口能直接用 LivingEntity 描述符，Fabric 照抄会崩）。</p>
     */
    @Inject(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void onHurtAndBreak(int amount, ServerLevel level, ServerPlayer player, Consumer<Item> onBreak,
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
