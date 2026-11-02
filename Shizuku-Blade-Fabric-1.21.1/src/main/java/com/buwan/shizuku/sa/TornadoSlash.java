package com.buwan.shizuku.sa;

import com.buwan.shizuku.util.DamageCalculator;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.buwan.shizuku.compat.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TornadoSlash {
    private static final int TORNADO_COLOR = 65416;
    private static final double PULL_RANGE = 25.0;
    private static final double DAMAGE_RANGE = 8.0;
    private static final int TORNADO_DURATION = 100;
    private static final float PULL_STRENGTH = 0.5F;
    private static final Map<UUID, TornadoData> activeTornados = new ConcurrentHashMap<>();

    public static void doTornadoSlash(LivingEntity entity) {
        if (!entity.level().isClientSide()) {
            if (entity instanceof Player player) {
                ServerLevel level = (ServerLevel) entity.level();
                Vec3 center = entity.position().add(entity.getViewVector(1.0F).scale(6.0));
                float damage = DamageCalculator.calculateRefinedDamage(entity, 0.6F);
                TornadoData tornadoData = new TornadoData(center, damage);
                activeTornados.put(player.getUUID(), tornadoData);
                level.playSound(null, center.x, center.y, center.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 2.0F, 0.5F);
            }
        }
    }

    
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel)) {
            return;
        }
        UUID uuid = player.getUUID();
        TornadoData tornadoData = activeTornados.get(uuid);
        if (tornadoData != null) {
            tornadoData.tickCounter++;
            tornadoData.rotationAngle += 30;
            ServerLevel level = (ServerLevel) player.level();
            if (tornadoData.tickCounter % 5 == 0) {
                pullEnemies(tornadoData, level, player);
                damageEnemies(player, tornadoData, level);
                createTornadoParticles(tornadoData, level);
                if (tornadoData.tickCounter % 20 == 0) {
                    level.playSound(
                            null,
                            tornadoData.center.x,
                            tornadoData.center.y,
                            tornadoData.center.z,
                            SoundEvents.PLAYER_ATTACK_SWEEP,
                            SoundSource.PLAYERS,
                            1.0F,
                            1.5F
                    );
                }
            }

            if (tornadoData.tickCounter >= TORNADO_DURATION) {
                finalBurst(player, tornadoData, level);
                activeTornados.remove(uuid);
            }
        }
    }

    private static void pullEnemies(TornadoData data, ServerLevel level, Player player) {
        for (LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(
                        data.center.x - PULL_RANGE,
                        data.center.y - 5.0,
                        data.center.z - PULL_RANGE,
                        data.center.x + PULL_RANGE,
                        data.center.y + 10.0,
                        data.center.z + PULL_RANGE
                ),
                entityx -> entityx != player && entityx.isAlive()
        )) {
            Vec3 toCenter = data.center.subtract(entity.position()).normalize();
            double distance = entity.position().distanceTo(data.center);
            double pullPower = PULL_STRENGTH * (1.0 - distance / PULL_RANGE);
            Vec3 pullVec = toCenter.scale(pullPower).add(0.0, pullPower * 0.3, 0.0);
            entity.setDeltaMovement(entity.getDeltaMovement().add(pullVec));
        }
    }

    private static void damageEnemies(Player player, TornadoData data, ServerLevel level) {
        for (LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(
                        data.center.x - DAMAGE_RANGE,
                        data.center.y - 3.0,
                        data.center.z - DAMAGE_RANGE,
                        data.center.x + DAMAGE_RANGE,
                        data.center.y + 5.0,
                        data.center.z + DAMAGE_RANGE
                ),
                entityx -> entityx != player && entityx.isAlive()
        )) {
            entity.hurt(level.damageSources().playerAttack(player), data.baseDamage * 0.2F);
            double angle = Math.toRadians(data.rotationAngle);
            Vec3 rotateVec = new Vec3(Math.cos(angle) * 0.2, 0.1, Math.sin(angle) * 0.2);
            entity.setDeltaMovement(entity.getDeltaMovement().add(rotateVec));
        }
    }

    private static void createTornadoParticles(TornadoData data, ServerLevel level) {
        for (int i = 0; i < 20; i++) {
            double height = Math.random() * 10.0;
            double radius = 1.5 + height * 0.4;
            double angle = Math.toRadians(data.rotationAngle + i * 18);
            double x = data.center.x + Math.cos(angle) * radius;
            double z = data.center.z + Math.sin(angle) * radius;
            double y = data.center.y + height;
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, 2, 0.1, 0.1, 0.1, 0.05);
            level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.SPIT, x, y, z, 3, 0.2, 0.2, 0.2, 0.02);
        }

        for (int i = 0; i < 32; i++) {
            double angle = (Math.PI * 2) * (double) i / 32.0;
            double x = data.center.x + Math.cos(angle) * 8.0;
            double z = data.center.z + Math.sin(angle) * 8.0;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, data.center.y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(data.rotationAngle * 2 + i * 36);
            double x = data.center.x + Math.cos(angle) * 0.5;
            double z = data.center.z + Math.sin(angle) * 0.5;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, data.center.y + 8.0, z, 2, 0.1, 0.1, 0.1, 0.05);
        }
    }

    private static void finalBurst(Player player, TornadoData data, ServerLevel level) {
        for (LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(
                        data.center.x - DAMAGE_RANGE,
                        data.center.y - 3.0,
                        data.center.z - DAMAGE_RANGE,
                        data.center.x + DAMAGE_RANGE,
                        data.center.y + 5.0,
                        data.center.z + DAMAGE_RANGE
                ),
                entityx -> entityx != player && entityx.isAlive()
        )) {
            entity.hurt(level.damageSources().playerAttack(player), data.baseDamage * 2.0F);
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, data.center.x, data.center.y + 2.0, data.center.z, 3, 1.0, 1.0, 1.0, 0.0);
        level.playSound(null, data.center.x, data.center.y, data.center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    public static void clearPlayerState(UUID playerUUID) {
        activeTornados.remove(playerUUID);
    }

    private static class TornadoData {
        final Vec3 center;
        int tickCounter;
        final float baseDamage;
        int rotationAngle;

        TornadoData(Vec3 center, float baseDamage) {
            this.center = center;
            this.tickCounter = 0;
            this.baseDamage = baseDamage;
            this.rotationAngle = 0;
        }
    }
}
