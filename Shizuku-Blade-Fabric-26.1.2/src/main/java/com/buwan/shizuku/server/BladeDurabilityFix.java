package com.buwan.shizuku.server;

import com.buwan.shizuku.sa.UnleashedJudgment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 修复无铭「有名」攻击时的负数附魔等级。
 *
 * <p>Fabric 版不再订阅 Forge 的 LivingAttackEvent：由 {@code AttackEntityCallback}
 * （在 {@code shizuku#init()} 里注册）调用 {@link #onLivingAttack(LivingEntity)}。</p>
 *
 * <p>1.20.1 的实现直接改 NBT 里的 {@code Enchantments} ListTag；1.21 起附魔改成
 * {@link net.minecraft.core.component.DataComponents#ENCHANTMENTS} 的
 * {@code ItemEnchantments} 组件，等级是 0..255 的整数、负数根本无法表达，
 * 因此该修复在 1.21 上不再适用（与 NeoForge 1.21.1 端口保持一致，保留空实现）。</p>
 */
public class BladeDurabilityFix {
    /** 攻击者挥击时（由 Fabric 的攻击回调传入攻击者）。 */
    public static void onLivingAttack(LivingEntity attacker) {
        if (attacker != null) {
            fixBladeEnchantmentLevels(attacker.getMainHandItem());
        }
    }

    public static void fixBladeEnchantmentLevels(ItemStack stack) {
        // no-op in 1.21.1：负数附魔等级无法用 ItemEnchantments 组件表达
        if (UnleashedJudgment.isBlade(stack)) {
            return;
        }
    }
}
