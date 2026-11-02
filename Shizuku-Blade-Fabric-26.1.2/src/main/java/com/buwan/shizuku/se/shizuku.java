package com.buwan.shizuku.se;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/** 喵喵（SE 33）：常驻力量/抗性，命中给速度与回复。事件在 shizuku.init() 中注册。 */
public class shizuku extends SpecialEffect {
    public shizuku() {
        super(33);
    }

    public static void onUpdate(SlashBladeEvent.UpdateEvent event) {
        ISlashBladeState state = event.getSlashBladeState();
        if (!state.hasSpecialEffect(SERegistry.SHIZUKU.getId())) {
            return;
        }

        if (event.getEntity() instanceof Player player
                && SpecialEffect.isEffective(SERegistry.SHIZUKU.getId(), player.experienceLevel)) {

            if (player.tickCount % 100 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 120, 2, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 1, false, false));
            }
        }
    }

    public static void onHitEntity(SlashBladeEvent.HitEvent event) {
        ISlashBladeState state = event.getSlashBladeState();
        if (!state.hasSpecialEffect(SERegistry.SHIZUKU.getId())) {
            return;
        }

        if (event.getUser() instanceof Player player
                && SpecialEffect.isEffective(SERegistry.SHIZUKU.getId(), player.experienceLevel)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 2, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, false, false));
        }
    }
}
