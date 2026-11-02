package com.buwan.shizuku.mixin;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ItemSlashBlade.class)
public class ItemSlashBladeMixin {

    /**
     * 1.20.1 的注入目标为 {@code ItemStack.hurtAndBreak(int, LivingEntity, Consumer<LivingEntity>)}；
     * 1.21.1 已改为 {@code hurtAndBreak(int, ServerLevel, LivingEntity, Consumer<Item>)}，此处更新目标描述符。
     * <p>
     * 1.20.1 版本中“修复负数附魔等级”依赖 NBT 的 Enchantments ListTag；
     * 1.21.1 附魔已改为数据组件 {@code ItemEnchantments}，无法通过 NBT 表达负等级，故该修复逻辑移除。
     */
    @Inject(
            method = {"hurtEnemy"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
                    shift = At.Shift.BEFORE
            )},
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 0
    )
    private void onHurtEnemyBeforeHurtBreak(ItemStack stack, LivingEntity target, LivingEntity attacker,
                                            CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!stack.isDamageableItem() || stack.getDamageValue() >= stack.getMaxDamage()) {
                System.out.println("[Shizuku] 跳过 hurtAndBreak：物品不可损坏或耐久度已满");
            }
        } catch (Exception ignored) {
        }
    }

    @Inject(
            method = {"mineBlock"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
                    shift = At.Shift.BEFORE
            )},
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 0
    )
    private void onMineBlockBeforeHurtBreak(ItemStack stack, Level worldIn, BlockState state, BlockPos pos,
                                            LivingEntity entityLiving, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!stack.isDamageableItem() || stack.getDamageValue() >= stack.getMaxDamage()) {
                System.out.println("[Shizuku] 跳过 hurtAndBreak：物品不可损坏或耐久度已满");
            }
        } catch (Exception ignored) {
        }
    }
}
