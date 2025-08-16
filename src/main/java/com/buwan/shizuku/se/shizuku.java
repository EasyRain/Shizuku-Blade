package com.buwan.shizuku.se;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class shizuku extends SpecialEffect {
    public shizuku() {
        super(100); // 效果ID需与注册ID匹配
    }

    // 常驻效果：每100tick（5秒）刷新增益（无音效/粒子）
    @SubscribeEvent
    public static void onUpdate(SlashBladeEvent.UpdateEvent event) {
        ISlashBladeState state = event.getSlashBladeState();
        if (!state.hasSpecialEffect(SERegistry.SHIZUKU.getId())) return;

        if (event.getEntity() instanceof Player player &&
                SpecialEffect.isEffective(SERegistry.SHIZUKU.getId(), player.experienceLevel)) {

            // 每5秒刷新增益
            if (player.tickCount % 100 == 0) {
                // 力量III（无粒子/无音效）
                player.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_BOOST, 120, 2, // 持续6秒
                        false, false
                ));

                // 抗性提升II（无粒子/无音效）
                player.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_RESISTANCE, 120, 1,
                        false, false
                ));


            }
        }
    }

    // 击中实体：赋予速度II（1秒，无音效/粒子）
    @SubscribeEvent
    public static void onHitEntity(SlashBladeEvent.HitEvent event) {
        ISlashBladeState state = event.getSlashBladeState();
        if (!state.hasSpecialEffect(SERegistry.SHIZUKU.getId())) return;

        if (event.getUser() instanceof Player player &&
                SpecialEffect.isEffective(SERegistry.SHIZUKU.getId(), player.experienceLevel)) {
            // 速度II效果（无粒子/无音效）
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED, 40, 2,
                    false, false
            ));
            // 生命恢复II（每1.25秒恢复1点生命，持续2秒）[3,4](@ref)
            player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, 40, 1, false, false // 等级1=恢复II
            ));
        }
    }
}