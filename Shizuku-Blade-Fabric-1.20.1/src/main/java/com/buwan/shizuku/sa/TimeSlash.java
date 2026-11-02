package com.buwan.shizuku.sa;

import com.buwan.shizuku.network.PacketHandler;
import com.buwan.shizuku.network.TimeStopPacket;
import com.buwan.shizuku.network.TimeStopShaderPacket;
import com.buwan.shizuku.util.DamageCalculator;
import mods.flammpfeil.slashblade.slasharts.SakuraEnd;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.buwan.shizuku.compat.PlayerTickEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TimeSlash {
    private static final int TIME_COLOR = 16766720;
    private static final double FREEZE_RANGE = 12.0;
    private static final int FREEZE_DURATION = 100;
    private static final int SLASH_COUNT = 8;
    private static final Map<UUID, TimeFreezeData> activeFreezes = new ConcurrentHashMap<>();

    public static void doTimeSlash(LivingEntity entity) {
        if (!entity.level().isClientSide()) {
            if (entity instanceof Player player) {
                ServerLevel level = (ServerLevel) entity.level();
                List<LivingEntity> targets = findTargetsInRange(entity, level);
                if (!targets.isEmpty()) {
                    for (LivingEntity target : targets) {
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FREEZE_DURATION, 255, false, false));
                        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, FREEZE_DURATION, 10, false, false));
                        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, FREEZE_DURATION, 255, false, false));
                        target.setDeltaMovement(Vec3.ZERO);
                    }

                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FREEZE_DURATION, 255, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, FREEZE_DURATION, 255, false, false));
                    player.setDeltaMovement(Vec3.ZERO);
                    level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 2.0F, 0.5F);
                    TimeFreezeData freezeData = new TimeFreezeData(targets);
                    freezeData.center = entity.position();
                    activeFreezes.put(player.getUUID(), freezeData);
                    if (player instanceof ServerPlayer serverPlayer) {
                        TimeStopPacket packet = new TimeStopPacket(player.getUUID(), entity.position(), FREEZE_RANGE, FREEZE_DURATION);
                        PacketHandler.sendToNearby(packet, serverPlayer, 64.0);
                        TimeStopShaderPacket shaderPacket = new TimeStopShaderPacket(true, 1.0F);
                        PacketHandler.sendToNearby(shaderPacket, serverPlayer, 128.0);
                    }
                }
            }
        }
    }

    
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel)) {
            return;
        }
        UUID uuid = player.getUUID();
        TimeFreezeData freezeData = activeFreezes.get(uuid);
        if (freezeData != null) {
            freezeData.tickCounter++;
            if (freezeData.tickCounter % 5 == 0) {
                for (LivingEntity target : freezeData.frozenTargets) {
                    if (target.isAlive()) {
                        target.setDeltaMovement(Vec3.ZERO);
                    }
                }

                player.setDeltaMovement(Vec3.ZERO);
            }

            int slashInterval = 12;
            if (freezeData.tickCounter % slashInterval == 0 && freezeData.slashCounter < SLASH_COUNT) {
                executeDelayedSlash(player, freezeData);
                freezeData.slashCounter++;
            }

            if (freezeData.tickCounter >= FREEZE_DURATION) {
                releaseAllDamage(player, freezeData);
                activeFreezes.remove(uuid);
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                player.removeEffect(MobEffects.DIG_SLOWDOWN);
                if (player instanceof ServerPlayer serverPlayer) {
                    TimeStopPacket removePacket = new TimeStopPacket(uuid);
                    PacketHandler.sendToNearby(removePacket, serverPlayer, 64.0);
                    TimeStopShaderPacket shaderPacket = new TimeStopShaderPacket(false, 0.0F);
                    PacketHandler.sendToNearby(shaderPacket, serverPlayer, 128.0);
                }
            }
        }
    }

    private static void executeDelayedSlash(Player player, TimeFreezeData freezeData) {
        ServerLevel level = (ServerLevel) player.level();

        for (LivingEntity target : freezeData.frozenTargets) {
            if (target.isAlive()) {
                float angle = (float) (Math.random() * 360.0);
                SakuraEnd.doSlash(
                        player, angle, TIME_COLOR, target.position().subtract(player.position()).add(0.0, 1.0, 0.0), true, false, 0.0, KnockBacks.cancel
                );
                level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 1.0, target.getZ(), 3, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    private static void releaseAllDamage(Player player, TimeFreezeData freezeData) {
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.5F, 1.8F);
        int validTargets = 0;
        int totalTargets = freezeData.frozenTargets.size();

        for (LivingEntity target : freezeData.frozenTargets) {
            if (target.isAlive() && !target.isRemoved() && target.level() == level) {
                double distance = target.position().distanceTo(freezeData.center);
                if (!(distance > 18.0)) {
                    float damage = DamageCalculator.calculateRefinedDamage(player, target, 4.0F);
                    target.hurt(level.damageSources().playerAttack(player), damage);
                    Vec3 slashOffset = target.position().subtract(player.position()).normalize().scale(0.5);
                    SakuraEnd.doSlash(player, 0.0F, TIME_COLOR, slashOffset.add(0.0, 1.0, 0.0), true, true, 0.0, KnockBacks.cancel);
                    level.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + 1.0, target.getZ(), 5, 0.5, 0.5, 0.5, 0.1);
                    level.sendParticles(ParticleTypes.POOF, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.5, 0.5, 0.5, 0.3);
                    validTargets++;
                }
            }
        }

        if (validTargets < totalTargets) {
            System.out.println("[TimeSlash] 统计: 冻结 " + totalTargets + " 个目标，实际伤害 " + validTargets + " 个");
        }
    }

    private static List<LivingEntity> findTargetsInRange(LivingEntity caster, ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(FREEZE_RANGE), entity -> entity != caster && entity.isAlive());
    }

    public static void clearPlayerState(UUID playerUUID) {
        activeFreezes.remove(playerUUID);
    }

    private static class TimeFreezeData {
        final List<LivingEntity> frozenTargets;
        int tickCounter;
        int slashCounter;
        Vec3 center;

        TimeFreezeData(List<LivingEntity> targets) {
            this.frozenTargets = targets;
            this.tickCounter = 0;
            this.slashCounter = 0;
            this.center = Vec3.ZERO;
        }
    }
}
