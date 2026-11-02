package com.buwan.shizuku.sa;

import com.buwan.shizuku.core.AgentHealthMethodHelper;
import com.buwan.shizuku.core.EntityHelper;
import com.buwan.shizuku.util.DamageCalculator;
import com.buwan.shizuku.util.HealthModifyHelper;
import mods.flammpfeil.slashblade.slasharts.SakuraEnd;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.buwan.shizuku.compat.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.buwan.shizuku.shizuku.GLASS_BREAK_SOUND;

public class SummerSlash {
    private static final Map<UUID, SummerSlashData> activeSlashes = new ConcurrentHashMap<>();
    private static final int SLASH_INTERVAL = 3;

    public static void doSummerSlash(LivingEntity playerIn) {
        if (!playerIn.level().isClientSide()) {
            Level level = playerIn.level();
            Vec3 lookAngle = playerIn.getViewVector(1.0F);
            List<SlashData> slashes = new ArrayList<>();
            int startColor = -43776;
            int endColor = -13312;

            for (int i = 0; i < 3; i++) {
                float angle = -30.0F + (float) i * 30.0F;
                int color = interpolateColor(startColor, endColor, (float) i / 2.0F);
                float damage = DamageCalculator.calculateRefinedDamage(playerIn, 1.0F);
                slashes.add(new SlashData(angle * 0.5F, color, new Vec3(0.0, 0.2, 0.0), true, i == 1, damage));
            }

            int group2Start = -13312;
            int group2End = -43606;

            for (int i = 0; i < 5; i++) {
                float angle = (float) i * 72.0F;
                float height = 0.3F + (float) i * 0.15F;
                int color = interpolateColor(group2Start, group2End, (float) i / 4.0F);
                float damage = DamageCalculator.calculateRefinedDamage(playerIn, 1.1F + (float) i * 0.05F);
                slashes.add(new SlashData(angle * 0.3F, color, new Vec3(0.0, height, 0.0), true, i == 2, damage));
            }

            int[] colors = new int[]{-43606, -5614081, -11162881, -11141206, -171};

            for (int i = 0; i < 5; i++) {
                float angle = -60.0F + (float) i * 30.0F;
                float height = i % 2 == 0 ? 0.3F : 0.7F;
                int color = colors[i];
                float damage = DamageCalculator.calculateRefinedDamage(playerIn, 1.3F);
                slashes.add(new SlashData(angle * 0.5F, color, new Vec3(0.0, height, 0.0), true, i == 2, damage));
            }

            SummerSlashData slashData = new SummerSlashData(playerIn, slashes);
            activeSlashes.put(playerIn.getUUID(), slashData);
            executeNextSlash(slashData);
        }
    }

    private static void executeNextSlash(SummerSlashData slashData) {
        if (slashData.currentIndex < slashData.slashes.size()) {
            SlashData slash = slashData.slashes.get(slashData.currentIndex);
            LivingEntity player = slashData.player;
            Level level = player.level();
            boolean isLastSlash = slashData.currentIndex == slashData.slashes.size() - 1;
            SakuraEnd.doSlash(player, slash.angle, slash.color, slash.offset, slash.silent && !isLastSlash, slash.critical, slash.damage, KnockBacks.toss);
            if (slashData.currentIndex >= 8) {
                applySummerEffect(player, level, 6.0F);
            }

            if (isLastSlash) {
                applyPercentageDamageToEnemies(player, level, 0.078);
                playGlassBreakSound(player, level);
            }

            slashData.currentIndex++;
        }
    }

    private static void playGlassBreakSound(LivingEntity player, Level level) {
        if (!level.isClientSide()) {
            SoundEvent glassBreakSound = BuiltInRegistries.SOUND_EVENT.get(GLASS_BREAK_SOUND.getId()).map(net.minecraft.core.Holder.Reference::value).orElse(null);
            if (glassBreakSound != null) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), glassBreakSound, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    private static void applySummerEffect(LivingEntity owner, Level level, float range) {
        Vec3 look = owner.getViewVector(1.0F).normalize();
        List<Entity> founds = new ArrayList<>();

        for (Entity entity : level.getEntities(owner, owner.getBoundingBox().inflate(range))) {
            if (entity instanceof LivingEntity) {
                Vec3 toTarget = entity.position().subtract(owner.position()).normalize();
                double dotProduct = look.dot(toTarget);
                if (dotProduct >= 0.707) {
                    founds.add(entity);
                }
            }
        }

        for (Entity entity : founds) {
            if (entity instanceof LivingEntity target) {
                target.setRemainingFireTicks(60);
                float burnDamage = 2.0F + (float) (Math.random() * 1.5);
                target.hurt(level.damageSources().onFire(), burnDamage);
            }
        }
    }

    private static void applyPercentageDamageToEnemies(LivingEntity owner, Level level, double percent) {
        double range = 6.0;
        Vec3 look = owner.getViewVector(1.0F).normalize();
        List<Entity> targets = new ArrayList<>();

        for (Entity entity : level.getEntities(owner, owner.getBoundingBox().inflate(range))) {
            if (entity instanceof LivingEntity && entity != owner) {
                LivingEntity target = (LivingEntity) entity;
                Vec3 toTarget = target.position().subtract(owner.position()).normalize();
                double targetAngle = Math.toDegrees(Math.acos(look.dot(toTarget)));
                if (targetAngle <= 90.0) {
                    targets.add(target);
                }
            }
        }

        for (Entity entity : targets) {
            LivingEntity target = (LivingEntity) entity;
            applyPercentageDamage(target, owner, level, percent);
        }
    }

    private static boolean isRestrictedBoss(LivingEntity target) {
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
        return entityId.contains("apollyon") || entityId.contains("apollon");
    }

    private static void applyPercentageDamage(LivingEntity target, LivingEntity attacker, Level level, double percent) {
        try {
            float currentHealth = target.getHealth();
            float maxHealth = target.getMaxHealth();
            float damage = (float) ((double) maxHealth * percent);
            float newHealth = currentHealth - damage;
            newHealth = Math.max(0.0F, newHealth);

            target.setSharedFlagOnFire(false);
            AgentHealthMethodHelper.forceSetHealth(target, newHealth, maxHealth);
            EntityHelper.forcedSetHealth(target, newHealth);
            HealthModifyHelper.entityDataModify(target, newHealth);
            HealthModifyHelper.traversalModify(target, attacker, newHealth);
            HealthModifyHelper.tagModify(target, newHealth);

            if (newHealth <= 0.0F) {
                target.hurt(level.damageSources().playerAttack(attacker instanceof Player ? (Player) attacker : null), 1000.0F);
            }

            if (attacker instanceof Player player) {
                player.sendSystemMessage(Component.translatable(String.format(" 对 %s 造成了 %.1f (%.1f%%) 百分比真伤！", target.getName().getString(), damage, percent * 100.0)));
            }
        } catch (Exception e) {
            System.err.println("[SummerSlash] 应用百分比真伤异常: " + e.getMessage());
            if (attacker instanceof Player) {
                target.hurt(level.damageSources().playerAttack((Player) attacker), 1000.0F);
            } else {
                target.hurt(level.damageSources().genericKill(), 1000.0F);
            }
        }
    }

    private static int interpolateColor(int startColor, int endColor, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int startR = startColor >> 16 & 0xFF;
        int startG = startColor >> 8 & 0xFF;
        int startB = startColor & 0xFF;
        int endR = endColor >> 16 & 0xFF;
        int endG = endColor >> 8 & 0xFF;
        int endB = endColor & 0xFF;
        int r = (int) ((float) startR + (float) (endR - startR) * t);
        int g = (int) ((float) startG + (float) (endG - startG) * t);
        int b = (int) ((float) startB + (float) (endB - startB) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel)) {
            return;
        }
        UUID uuid = player.getUUID();
        SummerSlashData slashData = activeSlashes.get(uuid);
        if (slashData != null) {
            slashData.tickCounter++;
            if (slashData.tickCounter >= SLASH_INTERVAL) {
                slashData.tickCounter = 0;
                executeNextSlash(slashData);
                if (slashData.currentIndex >= slashData.slashes.size()) {
                    activeSlashes.remove(uuid);
                }
            }
        }
    }

    public static void clearPlayerState(UUID playerUUID) {
        activeSlashes.remove(playerUUID);
    }

    private static class SlashData {
        final float angle;
        final int color;
        final Vec3 offset;
        final boolean silent;
        final boolean critical;
        final float damage;

        SlashData(float angle, int color, Vec3 offset, boolean silent, boolean critical, float damage) {
            this.angle = angle;
            this.color = color;
            this.offset = offset;
            this.silent = silent;
            this.critical = critical;
            this.damage = damage;
        }
    }

    private static class SummerSlashData {
        final LivingEntity player;
        final List<SlashData> slashes;
        int currentIndex;
        int tickCounter;

        SummerSlashData(LivingEntity player, List<SlashData> slashes) {
            this.player = player;
            this.slashes = slashes;
            this.currentIndex = 0;
            this.tickCounter = 0;
        }
    }
}
