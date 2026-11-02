package com.buwan.shizuku.util;

import mods.flammpfeil.slashblade.SlashBladeConfig;
import mods.flammpfeil.slashblade.util.AttackHelper;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public class DamageCalculator {

    public static float calculateRefinedDamage(LivingEntity caster, Entity target, float comboRatio) {
        return calculateRefinedDamage(caster, comboRatio);
    }

    public static float calculateRefinedDamage(LivingEntity caster, Entity target) {
        return calculateRefinedDamage(caster, 1.0F);
    }

    public static float calculateRefinedDamage(LivingEntity caster, float comboRatio) {
        if (!(caster instanceof Player)) {
            return 6.0F * comboRatio;
        }
        // 3.7.0：伤害改回拔刀剑原本的计算方式 —— 面板攻击 × comboRatio + 评分等级加成，
        // 再乘拔刀剑伤害缩放与全局伤害倍率（不再手动叠加锋利/力量/精炼等附魔加成）。
        double attack = caster.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double damage = (double) comboRatio * attack + (double) AttackHelper.getRankBonus(caster);
        damage *= (double) AttackManager.getSlashBladeDamageScale(caster)
                * SlashBladeConfig.SLASHBLADE_DAMAGE_MULTIPLIER.get();
        return (float) damage;
    }
}
