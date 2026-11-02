package com.buwan.shizuku.se;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/** 静心（SE 34）：命中时给予生命恢复。事件在 shizuku.init() 中注册。 */
public class Jingxin extends SpecialEffect {
    public Jingxin() {
        super(34);
    }

    public static void onHitEntity(SlashBladeEvent.HitEvent event) {
        ISlashBladeState state = event.getSlashBladeState();
        if (!state.hasSpecialEffect(SERegistry.JINGXIN.getId())) {
            return;
        }

        if (event.getUser() instanceof Player player
                && SpecialEffect.isEffective(SERegistry.JINGXIN.getId(), player.experienceLevel)) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, false));
        }
    }
}
