package com.buwan.shizuku;


import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;


public class SlashEffectUtils {
    public static boolean hasSpecialEffect(ItemStack stack, String effect) {
        ResourceLocation effectLoc = ResourceLocation.tryParse(effect);
        if (effectLoc == null) {
            return false;
        }
        return CapabilitySlashBlade.BLADESTATE.maybeGet(stack).map(s -> s.hasSpecialEffect(effectLoc)).orElse(false);
    }

    public static int addslashbladesaDamage(LivingEntity player, int damageex) {
        // 兼容旧的伤害计算入口：伤害系数统一由拔刀剑的攻击管理器计算，这里原样返回
        return damageex;
    }
}
