package com.buwan.shizuku.sa;

import com.buwan.shizuku.shizuku;
import com.buwan.shizuku.core.AgentHealthMethodHelper;
import com.buwan.shizuku.core.EntityHelper;
import com.buwan.shizuku.util.DamageCalculator;
import com.buwan.shizuku.util.HealthModifyHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import mods.flammpfeil.slashblade.slasharts.SakuraEnd;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 「夏日斩」：本文件最大的一个 SA，含多段斩击、粒子与音效编排，
 * playerTick 里按时间轴推进每一段。
 */
public class SummerSlash {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("shizuku/summerslash");
    private static final Map<UUID, SummerSlash.SummerSlashData> activeSlashes = new ConcurrentHashMap<>();
    private static final int SLASH_INTERVAL = 3;

    public static void doSummerSlash(LivingEntity playerIn) {
        if (!playerIn.level().isClientSide()) {
            Level level = playerIn.level();
            Vec3 lookAngle = playerIn.getLookAngle();
            List<SummerSlash.SlashData> slashes = new ArrayList<>();
            int startColor = -43776;
            int endColor = -13312;

            for (int i = 0; i < 3; i++) {
                float angle = -30.0F + i * 30.0F;
                int color = interpolateColor(startColor, endColor, i / 2.0F);
                float damage = DamageCalculator.calculateRefinedDamage(playerIn, 1.0F);
                slashes.add(new SummerSlash.SlashData(angle * 0.5F, color, new Vec3(0.0, 0.2, 0.0), true, i == 1, damage));
            }

            int var12 = -13312;
            endColor = -43606;

            for (int i = 0; i < 5; i++) {
                float angle = i * 72.0F;
                float height = 0.3F + i * 0.15F;
                int color = interpolateColor(var12, endColor, i / 4.0F);
                float damage = DamageCalculator.calculateRefinedDamage(playerIn, 1.1F + i * 0.05F);
                slashes.add(new SummerSlash.SlashData(angle * 0.3F, color, new Vec3(0.0, height, 0.0), true, i == 2, damage));
            }

            int[] colors = new int[]{-43606, -5614081, -11162881, -11141206, -171};

            for (int i = 0; i < 5; i++) {
                float angle = -60.0F + i * 30.0F;
                float height = i % 2 == 0 ? 0.3F : 0.7F;
                int color = colors[i];
                float damage = DamageCalculator.calculateRefinedDamage(playerIn, 1.3F);
                slashes.add(new SummerSlash.SlashData(angle * 0.5F, color, new Vec3(0.0, height, 0.0), true, i == 2, damage));
            }

            SummerSlash.SummerSlashData slashData = new SummerSlash.SummerSlashData(playerIn, slashes);
            activeSlashes.put(playerIn.getUUID(), slashData);
            executeNextSlash(slashData);
        }
    }

    private static void executeNextSlash(SummerSlash.SummerSlashData slashData) {
        if (slashData.currentIndex < slashData.slashes.size()) {
            SummerSlash.SlashData slash = slashData.slashes.get(slashData.currentIndex);
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
            SoundEvent glassBreakSound = (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(shizuku.GLASS_BREAK_SOUND.getId());
            if (glassBreakSound != null) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), glassBreakSound, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    private static void applySummerEffect(LivingEntity owner, Level level, float range) {
        Vec3 look = owner.getLookAngle().normalize();
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

        for (Entity entityx : founds) {
            if (entityx instanceof LivingEntity target) {
                target.setSecondsOnFire(3);
                float burnDamage = 2.0F + (float)(Math.random() * 1.5);
                target.hurt(level.damageSources().inFire(), burnDamage);
            }
        }
    }

    private static void applyPercentageDamageToEnemies(LivingEntity owner, Level level, double percent) {
        double range = 6.0;
        Vec3 look = owner.getLookAngle().normalize();
        List<Entity> targets = new ArrayList<>();

        for (Entity entity : level.getEntities(owner, owner.getBoundingBox().inflate(range))) {
            if (entity instanceof LivingEntity && entity != owner) {
                LivingEntity target = (LivingEntity)entity;
                Vec3 toTarget = target.position().subtract(owner.position()).normalize();
                double targetAngle = Math.toDegrees(Math.acos(look.dot(toTarget)));
                if (targetAngle <= 90.0) {
                    targets.add(target);
                }
            }
        }

        for (Entity entityx : targets) {
            LivingEntity target = (LivingEntity)entityx;
            applyPercentageDamage(target, owner, level, percent);
        }
    }

    private static boolean isRestrictedBoss(LivingEntity target) {
        try {
            String entityId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString();
            if (!entityId.contains("apollyon") && !entityId.contains("apollon")) {
                try {
                    Class<?> apollyonHelperClass = Class.forName("z1gned.goetyrevelation.util.ApollyonAbilityHelper");
                    if (apollyonHelperClass.isInstance(target)) {
                        return true;
                    }
                } catch (ClassNotFoundException var3) {
                }

                return false;
            } else {
                return true;
            }
        } catch (Exception var4) {
            return false;
        }
    }

    private static void applyPercentageDamage(LivingEntity target, LivingEntity attacker, Level level, double percent) {
        try {
            float currentHealth = target.getHealth();
            float maxHealth = target.getMaxHealth();
            float damage = (float)(maxHealth * percent);
            float newHealth = currentHealth - damage;
            newHealth = Math.max(0.0F, newHealth);
            boolean isRestricted = isRestrictedBoss(target);
            if (isRestricted) {
                boolean success = false;

                try {
                    Method method = target.getClass().getMethod("revelaionfix$setApollyonHealth", float.class);
                    method.setAccessible(true);
                    method.invoke(target, newHealth);
                    success = true;
                } catch (Exception var18) {
                }

                if (!success) {
                    try {
                        Field healthAccessorField = target.getClass().getDeclaredField("PIHOPJUBVLOBWXLJBSFVELGVBLEJCWB");
                        healthAccessorField.setAccessible(true);
                        EntityDataAccessor<Float> healthAccessor = (EntityDataAccessor<Float>)healthAccessorField.get(null);
                        target.getEntityData().set(healthAccessor, newHealth);
                        success = true;
                    } catch (Exception var17) {
                    }
                }

                if (!success) {
                    AgentHealthMethodHelper.forceSetHealth((Entity)target, newHealth, maxHealth);
                    success = true;
                }

                if (success && attacker instanceof Player player) {
                    DamageSource playerDamageSource = player.damageSources().playerAttack(player);
                    float adjustedDamage = Math.min(damage, 20.0F);
                    target.hurt(playerDamageSource, adjustedDamage);

                    try {
                        Field lastHurtByPlayerField = LivingEntity.class.getDeclaredField("f_20888_");
                        lastHurtByPlayerField.setAccessible(true);
                        lastHurtByPlayerField.set(target, player);
                        Field lastHurtByPlayerTimeField = LivingEntity.class.getDeclaredField("f_20889_");
                        lastHurtByPlayerTimeField.setAccessible(true);
                        lastHurtByPlayerTimeField.setInt(target, 0);
                    } catch (Exception var16) {
                    }
                }

                if (attacker instanceof Player player) {
                    player.sendSystemMessage(
                        Component.literal(
                            String.format(
                                " \u5bf9 %s \u9020\u6210\u4e86 %.1f (%.1f%%) \u767e\u5206\u6bd4\u771f\u4f24\uff01",
                                target.getName().getString(),
                                damage,
                                percent * 100.0
                            )
                        )
                    );
                }
            } else {
                AgentHealthMethodHelper.forceSetHealth((Entity)target, newHealth, maxHealth);
                EntityHelper.forcedSetHealth(target, newHealth);
                target.setHealth(newHealth);
                HealthModifyHelper.entityDataModify(target, newHealth);
                HealthModifyHelper.traversalModify(target, attacker, newHealth);
                HealthModifyHelper.tagModify(target, newHealth);
                target.setInvulnerable(false);
                target.getPersistentData().remove("Immortal");
                target.getPersistentData().remove("Protected");
                target.getPersistentData().remove("Invincible");
                if (attacker instanceof Player player) {
                    player.sendSystemMessage(
                        Component.literal(
                            String.format(
                                " \u5bf9 %s \u9020\u6210\u4e86 %.1f (%.1f%%) \u767e\u5206\u6bd4\u771f\u4f24\uff01",
                                target.getName().getString(),
                                damage,
                                percent * 100.0
                            )
                        )
                    );
                }
            }
        } catch (Exception var19) {
            System.err.println("[SummerSlash] \u5e94\u7528\u767e\u5206\u6bd4\u771f\u4f24\u5f02\u5e38: " + var19.getMessage());
            LOGGER.debug("unexpected error", var19);
            if (attacker instanceof Player) {
                target.hurt(level.damageSources().playerAttack((Player)attacker), 1000.0F);
            } else {
                target.hurt(level.damageSources().generic(), 1000.0F);
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
        int r = (int)(startR + (endR - startR) * t);
        int g = (int)(startG + (endG - startG) * t);
        int b = (int)(startB + (endB - startB) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static Vec3 rotateY(Vec3 vec, float angleDegrees) {
        float angleRad = angleDegrees * (float) (Math.PI / 180.0);
        float cos = Mth.cos(angleRad);
        float sin = Mth.sin(angleRad);
        return new Vec3(vec.x * cos - vec.z * sin, vec.y, vec.x * sin + vec.z * cos);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {
        if (!event.side.isClient() && event.phase == Phase.END) {
            Player player = event.player;
            UUID uuid = player.getUUID();
            SummerSlash.SummerSlashData slashData = activeSlashes.get(uuid);
            if (slashData != null) {
                slashData.tickCounter++;
                if (slashData.tickCounter >= 3) {
                    slashData.tickCounter = 0;
                    executeNextSlash(slashData);
                    if (slashData.currentIndex >= slashData.slashes.size()) {
                        activeSlashes.remove(uuid);
                    }
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
        final List<SummerSlash.SlashData> slashes;
        int currentIndex;
        int tickCounter;

        SummerSlashData(LivingEntity player, List<SummerSlash.SlashData> slashes) {
            this.player = player;
            this.slashes = slashes;
            this.currentIndex = 0;
            this.tickCounter = 0;
        }
    }
}
