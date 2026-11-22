package com.buwan.shizuku.se;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = com.buwan.shizuku.shizuku.MODID)
public class WorldsFavorite extends SpecialEffect {

    public WorldsFavorite() {
        super(0);
    }

    // 1.20.1 的 LivingHurtEvent/LivingAttackEvent 在 1.21.1 中合并为 LivingIncomingDamageEvent
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity != null && hasWorldsFavorite(entity)) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            entity.setHealth(entity.getMaxHealth());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (entity != null && hasWorldsFavorite(entity)) {
            event.setNewDamage(0.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPotionEffectAdded(MobEffectEvent.Added event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || !hasWorldsFavorite(entity)) {
            return;
        }
        MobEffectInstance effectInstance = event.getEffectInstance();
        if (effectInstance != null) {
            Holder<MobEffect> effect = effectInstance.getEffect();
            if (!effect.value().isBeneficial()) {
                entity.removeEffect(effect);
            }
        }
    }

    private static void applyAntiApollyonProtection(Player player) {
        player.setHealth(player.getMaxHealth());
        player.getPersistentData().putBoolean("CanHeal", true);
        player.getPersistentData().putBoolean("CanRegenerate", true);

        AttributeInstance movementAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementAttr != null) {
            for (AttributeModifier modifier : new ArrayList<>(movementAttr.getModifiers())) {
                if (modifier.amount() < 0.0) {
                    movementAttr.removeModifier(modifier.id());
                }
            }
        }

        AttributeInstance knockbackAttr = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) {
            for (AttributeModifier modifier : new ArrayList<>(knockbackAttr.getModifiers())) {
                if (modifier.amount() > 0.0) {
                    knockbackAttr.removeModifier(modifier.id());
                }
            }
        }

        player.invulnerableTime = 0;
        for (MobEffectInstance effectInstance : new ArrayList<>(player.getActiveEffects())) {
            MobEffect effect = effectInstance.getEffect().value();
            if (!effect.isBeneficial()) {
                player.removeEffect(effectInstance.getEffect());
            }
        }
        player.noPhysics = false;
        player.setNoGravity(false);
    }

    private static boolean hasWorldsFavorite(LivingEntity entity) {
        return hasWorldsFavoriteStatic(entity);
    }

    public static boolean hasWorldsFavoriteStatic(LivingEntity entity) {
        ItemStack mainHand = entity.getMainHandItem();
        if (!mainHand.isEmpty() && BladeStateAccess.of(mainHand).isPresent()) {
            if (BladeStateAccess.of(mainHand).map(state -> state.hasSpecialEffect(SERegistry.WORLDS_FAVORITE.getId())
                    && SpecialEffect.isEffective(SERegistry.WORLDS_FAVORITE.getId(),
                    entity instanceof Player p ? p.experienceLevel : 0)).orElse(false)) {
                return true;
            }
        }

        ItemStack offHand = entity.getOffhandItem();
        if (!offHand.isEmpty() && BladeStateAccess.of(offHand).isPresent()) {
            if (BladeStateAccess.of(offHand).map(state -> state.hasSpecialEffect(SERegistry.WORLDS_FAVORITE.getId())
                    && SpecialEffect.isEffective(SERegistry.WORLDS_FAVORITE.getId(),
                    entity instanceof Player p ? p.experienceLevel : 0)).orElse(false)) {
                return true;
            }
        }

        return false;
    }

}
