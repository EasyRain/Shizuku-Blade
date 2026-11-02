package com.buwan.shizuku.se;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/** 大雷（SE 32）：每 5 秒给予跳跃提升与力量。事件在 shizuku.init() 中注册。 */
public class Leise extends SpecialEffect {
    public Leise() {
        super(32);
    }

    public static void onUpdate(SlashBladeEvent.UpdateEvent event) {
        ISlashBladeState state = event.getSlashBladeState();
        if (!state.hasSpecialEffect(SERegistry.LEISE.getId())) {
            return;
        }

        if (event.getEntity() instanceof Player player
                && SpecialEffect.isEffective(SERegistry.LEISE.getId(), player.experienceLevel)) {

            if (player.tickCount % 100 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 120, 1, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, 2, false, false));
            }
        }
    }
}
