package com.buwan.shizuku.server;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = com.buwan.shizuku.shizuku.MODID)
public class BladeDurabilityFix {

    @SubscribeEvent
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            ItemStack stack = attacker.getMainHandItem();
            if (stack.getItem() instanceof ItemSlashBlade) {
                fixBladeEnchantmentLevels(stack);
            }
        }
    }

    /**
     * 1.20.1 版本通过修改 NBT 中的 Enchantments ListTag 修复负数附魔等级。
     * 1.21.1 中附魔改为数据组件 ItemEnchantments，负等级无法通过 NBT 表达，
     * 因此该修复逻辑不再适用，此处保留空实现。
     */
    private static void fixBladeEnchantmentLevels(ItemStack stack) {
        // no-op in 1.21.1
    }
}
