package com.buwan.shizuku.se;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = com.buwan.shizuku.shizuku.MODID)
public class Jingxin extends SpecialEffect {
    public Jingxin() {
        super(34);
    }

    @SubscribeEvent
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
