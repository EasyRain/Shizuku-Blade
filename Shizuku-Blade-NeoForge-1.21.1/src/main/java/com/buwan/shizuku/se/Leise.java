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
public class Leise extends SpecialEffect {
    public Leise() {
        super(32); // 与 1.20.1 原版一致的 SE 等级（大雷~）
    }

    // 常驻效果：每5秒给予跳跃提升和缓降效果
    @SubscribeEvent
    public static void onUpdate(SlashBladeEvent.UpdateEvent event) {
        ISlashBladeState state = event.getSlashBladeState();
        if (!state.hasSpecialEffect(SERegistry.LEISE.getId())) return;

        if (event.getEntity() instanceof Player player &&
                SpecialEffect.isEffective(SERegistry.LEISE.getId(), player.experienceLevel)) {

            // 每5秒刷新效果
            if (player.tickCount % 100 == 0) {
                // 跳跃提升II（无粒子/无音效）
                player.addEffect(new MobEffectInstance(
                        MobEffects.JUMP, 120, 1,
                        false, false
                ));
                player.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_BOOST, 120, 2, // 持续6秒
                        false, false
                ));


            }
        }
    }



}
