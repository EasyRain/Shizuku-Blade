package com.buwan.shizuku.sa;

import com.buwan.shizuku.util.DamageCalculator;
import mods.flammpfeil.slashblade.init.SBEntityTypes;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.buwan.shizuku.compat.ServerTickEvent;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Yingsa {
    private static final Map<EntityAbstractSummonedSword, DelayedShootData> delayedSwords = new ConcurrentHashMap<>();

    public static void doYingSa(LivingEntity entity) {
        if (!entity.level().isClientSide()) {
            List<Entity> foundTargets = findTargets(entity);
            summonPhantomBlades(entity, foundTargets, 25);
        }
    }

    private static List<Entity> findTargets(LivingEntity caster) {
        if (!(caster instanceof Player)) {
            return Collections.emptyList();
        } else {
            ServerLevel serverLevel = (ServerLevel) caster.level();
            Vec3 casterPos = caster.getEyePosition(1.0F);
            Vec3 casterLookDirection = caster.getViewVector(1.0F);
            return serverLevel.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(15.0), entity -> entity != caster && entity.isAlive())
                    .stream()
                    .filter(entity -> {
                        Vec3 entityPos = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
                        Vec3 directionToEntity = entityPos.subtract(casterPos).normalize();
                        double dotProduct = directionToEntity.dot(casterLookDirection);
                        return dotProduct >= 0.707;
                    })
                    .sorted((e1, e2) -> {
                        Vec3 pos1 = e1.position().add(0.0, e1.getBbHeight() * 0.5, 0.0);
                        Vec3 pos2 = e2.position().add(0.0, e2.getBbHeight() * 0.5, 0.0);
                        Vec3 dir1 = pos1.subtract(casterPos).normalize();
                        Vec3 dir2 = pos2.subtract(casterPos).normalize();
                        double angle1 = dir1.dot(casterLookDirection);
                        double angle2 = dir2.dot(casterLookDirection);
                        int angleCompare = Double.compare(angle2, angle1);
                        if (angleCompare != 0) {
                            return angleCompare;
                        } else {
                            double dist1 = caster.distanceTo(e1);
                            double dist2 = caster.distanceTo(e2);
                            return Double.compare(dist1, dist2);
                        }
                    })
                    .limit(1L)
                    .map(entity -> (Entity) entity)
                    .collect(Collectors.toList());
        }
    }

    private static void summonPhantomBlades(LivingEntity caster, List<Entity> foundTargets, int count) {
        Level world = caster.level();
        if (!world.isClientSide()) {
            for (int i = 0; i < count; i++) {
                EntityAbstractSummonedSword phantomBlade = new EntityAbstractSummonedSword(SBEntityTypes.SUMMONED_SWORD, world);
                Vec3 spawnPos;
                Vec3 direction;
                if (!foundTargets.isEmpty()) {
                    Entity target = foundTargets.get(i % foundTargets.size());
                    Vec3 targetPos = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
                    double baseRadius = 8.0;
                    double radiusVariation = (Math.random() - 0.5) * 4.0;
                    double radius = baseRadius + radiusVariation;
                    double theta = (double) i * 2.399 + Math.random() * 0.5;
                    double phi = Math.acos(1.0 - 2.0 * ((double) i + 0.5) / (double) count);
                    theta += (Math.random() - 0.5) * 0.6;
                    phi += (Math.random() - 0.5) * 0.4;
                    double offsetX = radius * Math.sin(phi) * Math.cos(theta);
                    double offsetY = radius * Math.cos(phi);
                    double offsetZ = radius * Math.sin(phi) * Math.sin(theta);
                    spawnPos = targetPos.add(offsetX, offsetY, offsetZ);
                    direction = targetPos.subtract(spawnPos).normalize();
                } else {
                    double randomX = (Math.random() - 0.5) * 6.0;
                    double randomY = Math.random() * 3.0;
                    double randomZ = (Math.random() - 0.5) * 6.0;
                    spawnPos = caster.position().add(randomX, randomY, randomZ);
                    double randomYaw = Math.random() * 2.0 * Math.PI;
                    double x = Math.cos(randomYaw);
                    double z = Math.sin(randomYaw);
                    direction = new Vec3(x, 0.0, z);
                }

                phantomBlade.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                phantomBlade.setOwner(caster);
                float refinedDamage = DamageCalculator.calculateRefinedDamage(caster, 0.6F);
                phantomBlade.setDamage(refinedDamage);
                phantomBlade.setColor(16716947);
                phantomBlade.setPierce((byte) 0);
                phantomBlade.setIsCritical(true);
                phantomBlade.setRoll(0.0F);
                float shootSpeed = 2.5F;
                int delayTicks = 12;
                delayedSwords.put(phantomBlade, new DelayedShootData(direction, shootSpeed, delayTicks));
                world.addFreshEntity(phantomBlade);
            }
        }
    }

    
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<EntityAbstractSummonedSword, DelayedShootData>> iterator = delayedSwords.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<EntityAbstractSummonedSword, DelayedShootData> entry = iterator.next();
            EntityAbstractSummonedSword sword = entry.getKey();
            DelayedShootData data = entry.getValue();
            if (!sword.isRemoved() && sword.isAlive()) {
                data.delayTicks--;
                if (data.delayTicks <= 0) {
                    sword.shoot(data.direction.x, data.direction.y, data.direction.z, data.speed, 0.0F);
                    iterator.remove();
                }
            } else {
                iterator.remove();
            }
        }
    }

    public static void clearPlayerState(UUID playerUUID) {
        delayedSwords.entrySet().removeIf(entry -> {
            EntityAbstractSummonedSword sword = entry.getKey();
            return sword != null && sword.getOwner() != null && sword.getOwner().getUUID().equals(playerUUID);
        });
    }

    private static class DelayedShootData {
        Vec3 direction;
        float speed;
        int delayTicks;

        DelayedShootData(Vec3 direction, float speed, int delayTicks) {
            this.direction = direction;
            this.speed = speed;
            this.delayTicks = delayTicks;
        }
    }
}
