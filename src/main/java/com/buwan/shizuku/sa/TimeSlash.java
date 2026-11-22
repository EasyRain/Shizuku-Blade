package com.buwan.shizuku.sa;

import com.buwan.shizuku.network.PacketHandler;
import com.buwan.shizuku.network.TimeStopPacket;
import com.buwan.shizuku.network.TimeStopShaderPacket;
import com.buwan.shizuku.util.DamageCalculator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「时停」：以自身为中心 FREEZE_RANGE 范围内冻结敌人 FREEZE_DURATION tick，
 * 并通过 TimeStopPacket / TimeStopShaderPacket 同步客户端观感。
 */
public class TimeSlash {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("shizuku/timeslash");
    private static final int TIME_COLOR = 16766720;
    private static final double FREEZE_RANGE = 12.0;
    private static final int FREEZE_DURATION = 100;
    private static final int SLASH_COUNT = 8;
    private static final Map<UUID, TimeSlash.TimeFreezeData> activeFreezes = new ConcurrentHashMap<>();

    public static void doTimeSlash(LivingEntity entity) {
        if (!entity.level().isClientSide()) {
            if (entity instanceof Player player) {
                ServerLevel level = (ServerLevel)entity.level();
                List<LivingEntity> targets = findTargetsInRange(entity, level);
                if (!targets.isEmpty()) {
                    for (LivingEntity target : targets) {
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 255, false, false));
                        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 10, false, false));
                        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 255, false, false));
                        target.setDeltaMovement(Vec3.ZERO);
                    }

                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 255, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 255, false, false));
                    player.setDeltaMovement(Vec3.ZERO);
                    level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 2.0F, 0.5F);
                    TimeSlash.TimeFreezeData freezeData = new TimeSlash.TimeFreezeData(targets);
                    freezeData.center = entity.position();
                    activeFreezes.put(player.getUUID(), freezeData);
                    if (player instanceof ServerPlayer serverPlayer) {
                        TimeStopPacket packet = new TimeStopPacket(player.getUUID(), entity.position(), 12.0, 100);
                        PacketHandler.sendToNearby(packet, serverPlayer, 64.0);
                        TimeStopShaderPacket shaderPacket = new TimeStopShaderPacket(true, 1.0F);
                        PacketHandler.sendToNearby(shaderPacket, serverPlayer, 128.0);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {
        if (!event.side.isClient() && event.phase == Phase.END) {
            Player player = event.player;
            UUID uuid = player.getUUID();
            TimeSlash.TimeFreezeData freezeData = activeFreezes.get(uuid);
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
                if (freezeData.tickCounter % slashInterval == 0 && freezeData.slashCounter < 8) {
                    executeDelayedSlash(player, freezeData);
                    freezeData.slashCounter++;
                }

                if (freezeData.tickCounter >= 100) {
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
    }

    private static void executeDelayedSlash(Player player, TimeSlash.TimeFreezeData freezeData) {
        ServerLevel level = (ServerLevel)player.level();

        for (LivingEntity target : freezeData.frozenTargets) {
            if (target.isAlive()) {
                float angle = (float)(Math.random() * 360.0);
                SakuraEnd.doSlash(
                    player, angle, 16766720, target.position().subtract(player.position()).add(0.0, 1.0, 0.0), true, false, 0.0, KnockBacks.cancel
                );
                level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1.0, target.getZ(), 3, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    private static void releaseAllDamage(Player player, TimeSlash.TimeFreezeData freezeData) {
        ServerLevel level = (ServerLevel)player.level();
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
                    SakuraEnd.doSlash(player, 0.0F, 16766720, slashOffset.add(0.0, 1.0, 0.0), true, true, 0.0, KnockBacks.cancel);
                    level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1.0, target.getZ(), 5, 0.5, 0.5, 0.5, 0.1);
                    level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.5, 0.5, 0.5, 0.3);
                    validTargets++;
                }
            }
        }

        if (validTargets < totalTargets) {
            LOGGER.debug(
                    "[TimeSlash] \u7edf\u8ba1: \u51bb\u7ed3 "
                        + totalTargets
                        + " \u4e2a\u76ee\u6807\uff0c\u5b9e\u9645\u4f24\u5bb3 "
                        + validTargets
                        + " \u4e2a\uff0c\u5931\u6548 "
                        + (totalTargets - validTargets)
                        + " \u4e2a"
                );
        }
    }

    private static List<LivingEntity> findTargetsInRange(LivingEntity caster, ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(12.0), entity -> entity != caster && entity.isAlive());
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
