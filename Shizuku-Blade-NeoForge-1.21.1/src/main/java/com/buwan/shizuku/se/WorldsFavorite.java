package com.buwan.shizuku.se;

import com.mojang.blaze3d.platform.InputConstants;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
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

    private static final Map<UUID, Boolean> coordinateShiftMode = new HashMap<>();
    private static final double SHIFT_SPEED = 0.5;

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

    private static boolean hasWorldsFavorite(LivingEntity entity) {
        return hasWorldsFavoriteStatic(entity);
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) {
            return;
        }
        if (!hasWorldsFavorite(mc.player)) {
            return;
        }

        int keyCode = event.getKey();
        if ((keyCode == 342 || keyCode == 346) && event.getAction() == 1) {
            long window = mc.getWindow().getWindow();
            boolean shiftDown = InputConstants.isKeyDown(window, 340) || InputConstants.isKeyDown(window, 344);
            if (shiftDown) {
                UUID playerId = mc.player.getUUID();
                boolean currentMode = coordinateShiftMode.getOrDefault(playerId, false);
                coordinateShiftMode.put(playerId, !currentMode);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCoordinateShiftTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        UUID playerId = player.getUUID();
        boolean modeEnabled = coordinateShiftMode.getOrDefault(playerId, false);
        if (modeEnabled && !hasWorldsFavorite(player)) {
            coordinateShiftMode.put(playerId, false);
            return;
        }
        if (!modeEnabled) {
            return;
        }

        double dx = 0.0;
        double dy = 0.0;
        double dz = 0.0;
        long window = mc.getWindow().getWindow();
        if (InputConstants.isKeyDown(window, 87)) {
            dz += SHIFT_SPEED;
        }
        if (InputConstants.isKeyDown(window, 83)) {
            dz -= SHIFT_SPEED;
        }
        if (InputConstants.isKeyDown(window, 65)) {
            dx += SHIFT_SPEED;
        }
        if (InputConstants.isKeyDown(window, 68)) {
            dx -= SHIFT_SPEED;
        }
        if (InputConstants.isKeyDown(window, 32)) {
            dy += SHIFT_SPEED;
        }
        if (InputConstants.isKeyDown(window, 340)) {
            dy -= SHIFT_SPEED;
        }

        if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
            float rotation = player.getYRot();
            double cos = Math.cos(Math.toRadians(rotation));
            double sin = Math.sin(Math.toRadians(rotation));
            double rotatedDx = dx * cos + dz * sin;
            double rotatedDz = -dx * sin + dz * cos;
            Vec3 newPos = player.position().add(rotatedDx, dy, rotatedDz);
            var connection = mc.getConnection();
            if (connection != null) {
                connection.sendUnsignedCommand(
                        String.format("tp @s %f %f %f", newPos.x, newPos.y, newPos.z));
            }
            player.setPos(newPos.x, newPos.y, newPos.z);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        coordinateShiftMode.remove(event.getEntity().getUUID());
    }
}
