package com.buwan.shizuku.sa;

import com.buwan.shizuku.util.DamageCalculator;
import mods.flammpfeil.slashblade.slasharts.SakuraEnd;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.buwan.shizuku.compat.PlayerTickEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportSlash {
    private static final int TELEPORT_COLOR = 65535;
    private static final double SEARCH_RANGE = 15.0;
    private static final double VIEW_ANGLE = 0.707;
    private static final int MAX_TARGETS = 5;
    private static final int DELAY_TICKS = 10;
    private static final int MAX_DURATION_TICKS = 160;
    private static final Map<UUID, TeleportChainData> activeChains = new ConcurrentHashMap<>();

    public static void doTeleportSlash(LivingEntity entity) {
        if (!entity.level().isClientSide()) {
            if (entity instanceof Player player) {
                ServerLevel serverLevel = (ServerLevel) entity.level();
                List<LivingEntity> targets = findMultipleTargets(entity, serverLevel);
                if (!targets.isEmpty()) {
                    float damage = DamageCalculator.calculateRefinedDamage(entity, 1.0F);
                    TeleportChainData chainData = new TeleportChainData(targets, damage);
                    activeChains.put(player.getUUID(), chainData);
                    executeChainAttack(player, chainData, serverLevel);
                } else {
                    teleportForward(entity, serverLevel);
                    playTeleportEffects(serverLevel, entity.position(), entity.position());
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
        TeleportChainData chainData = activeChains.get(uuid);
        if (chainData != null) {
            chainData.tickCounter++;
            chainData.totalTicks++;
            if (chainData.totalTicks >= MAX_DURATION_TICKS) {
                activeChains.remove(uuid);
                return;
            }

            if (chainData.tickCounter >= DELAY_TICKS) {
                chainData.tickCounter = 0;
                chainData.currentIndex++;
                if (chainData.currentIndex < chainData.targets.size()) {
                    executeChainAttack(player, chainData, (ServerLevel) player.level());
                } else {
                    activeChains.remove(uuid);
                }
            }
        }
    }

    private static void executeChainAttack(Player player, TeleportChainData chainData, ServerLevel level) {
        LivingEntity target = chainData.targets.get(chainData.currentIndex);
        if (!target.isAlive()) {
            chainData.currentIndex++;
            if (chainData.currentIndex < chainData.targets.size()) {
                executeChainAttack(player, chainData, level);
            } else {
                activeChains.remove(player.getUUID());
            }
        } else {
            Vec3 startPos = player.position();
            teleportBehindTarget(player, target, level);
            playTeleportEffects(level, startPos, player.position());
            performSlash(player, target, chainData.baseDamage);
        }
    }

    private static List<LivingEntity> findMultipleTargets(LivingEntity caster, ServerLevel level) {
        Vec3 casterPos = caster.getEyePosition(1.0F);
        Vec3 lookDirection = caster.getViewVector(1.0F);
        return level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(SEARCH_RANGE), entity -> entity != caster && entity.isAlive()).stream().filter(entity -> {
            Vec3 entityPos = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
            Vec3 directionToEntity = entityPos.subtract(casterPos).normalize();
            double dotProduct = directionToEntity.dot(lookDirection);
            return dotProduct >= VIEW_ANGLE;
        }).sorted((e1, e2) -> {
            Vec3 pos1 = e1.position().add(0.0, e1.getBbHeight() * 0.5, 0.0);
            Vec3 pos2 = e2.position().add(0.0, e2.getBbHeight() * 0.5, 0.0);
            Vec3 dir1 = pos1.subtract(casterPos).normalize();
            Vec3 dir2 = pos2.subtract(casterPos).normalize();
            double angle1 = dir1.dot(lookDirection);
            double angle2 = dir2.dot(lookDirection);
            return Double.compare(angle2, angle1);
        }).limit(MAX_TARGETS).toList();
    }

    private static void teleportBehindTarget(LivingEntity entity, LivingEntity target, ServerLevel level) {
        Vec3 targetLook = target.getViewVector(1.0F).normalize();
        Vec3 behindPos = target.position().add(targetLook.scale(-2.0)).add(0.0, 0.1, 0.0);
        Vec3 lookAt = target.position().subtract(behindPos).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-lookAt.x, lookAt.z));
        float pitch = (float) Math.toDegrees(Math.asin(-lookAt.y));
        if (entity instanceof ServerPlayer serverPlayer) {
            // 必须用 teleportTo：向客户端发送位置同步包并锁定位置确认，
            // 否则客户端移动包会在下一 tick 把服务器端玩家位置“回滚”，
            // 导致以玩家坐标为伤害中心的斩击打不到远处目标。
            serverPlayer.teleportTo(level, behindPos.x, behindPos.y, behindPos.z, java.util.Set.of(), yaw, pitch, false);
        } else {
            entity.setPos(behindPos.x, behindPos.y, behindPos.z);
            entity.setYRot(yaw);
            entity.setXRot(pitch);
            entity.setYHeadRot(yaw);
        }
    }

    private static void teleportForward(LivingEntity entity, ServerLevel level) {
        Vec3 look = entity.getViewVector(1.0F).normalize();
        Vec3 newPos = entity.position().add(look.scale(5.0));
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.teleportTo(level, newPos.x, newPos.y, newPos.z, java.util.Set.of(), serverPlayer.getYRot(), serverPlayer.getXRot(), false);
        } else {
            entity.setPos(newPos.x, newPos.y, newPos.z);
        }
    }

    private static void playTeleportEffects(ServerLevel level, Vec3 startPos, Vec3 endPos) {
        for (int i = 0; i < 20; i++) {
            double offsetX = (Math.random() - 0.5) * 1.0;
            double offsetY = Math.random() * 2.0;
            double offsetZ = (Math.random() - 0.5) * 1.0;
            level.sendParticles(ParticleTypes.PORTAL, startPos.x + offsetX, startPos.y + offsetY, startPos.z + offsetZ, 1, 0.0, 0.0, 0.0, 0.1);
        }

        for (int i = 0; i < 20; i++) {
            double offsetX = (Math.random() - 0.5) * 1.0;
            double offsetY = Math.random() * 2.0;
            double offsetZ = (Math.random() - 0.5) * 1.0;
            level.sendParticles(ParticleTypes.PORTAL, endPos.x + offsetX, endPos.y + offsetY, endPos.z + offsetZ, 1, 0.0, 0.0, 0.0, 0.1);
        }

        level.playSound(null, startPos.x, startPos.y, startPos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, endPos.x, endPos.y, endPos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    private static void performSlash(LivingEntity entity, LivingEntity target, float damage) {
        SakuraEnd.doSlash(entity, 0.0F, TELEPORT_COLOR, new Vec3(0.0, 0.2, 0.5), false, true, damage, KnockBacks.toss);
        SakuraEnd.doSlash(entity, 45.0F, TELEPORT_COLOR, new Vec3(0.0, 0.1, 0.7), true, true, damage * 0.8F, KnockBacks.toss);
        SakuraEnd.doSlash(entity, -45.0F, TELEPORT_COLOR, new Vec3(0.0, 0.1, 0.7), true, true, damage * 0.8F, KnockBacks.toss);
    }

    public static void clearPlayerState(UUID playerUUID) {
        activeChains.remove(playerUUID);
    }

    private static class TeleportChainData {
        final List<LivingEntity> targets;
        int currentIndex;
        int tickCounter;
        int totalTicks;
        final float baseDamage;

        TeleportChainData(List<LivingEntity> targets, float baseDamage) {
            this.targets = targets;
            this.currentIndex = 0;
            this.tickCounter = 0;
            this.totalTicks = 0;
            this.baseDamage = baseDamage;
        }
    }
}
