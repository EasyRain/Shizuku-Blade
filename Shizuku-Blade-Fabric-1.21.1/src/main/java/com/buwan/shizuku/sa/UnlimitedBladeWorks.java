package com.buwan.shizuku.sa;

import com.buwan.shizuku.util.DamageCalculator;
import mods.flammpfeil.slashblade.init.SBEntityTypes;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.buwan.shizuku.compat.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UnlimitedBladeWorks {
    private static final int SWORD_COLOR = 16737095;
    private static final int SWORD_COUNT = 30;
    private static final double SWORD_HEIGHT = 15.0;
    private static final int DEPLOY_DURATION = 20;
    private static final int FIRE_DURATION = 80;
    private static final Map<UUID, UBWData> activeUBW = new ConcurrentHashMap<>();

    public static void doUnlimitedBladeWorks(LivingEntity entity) {
        if (!entity.level().isClientSide()) {
            if (entity instanceof Player player) {
                ServerLevel level = (ServerLevel) entity.level();
                List<LivingEntity> targets = findAllTargets(entity, level);
                if (!targets.isEmpty()) {
                    List<Vec3> swordPositions = calculateSwordPositionsAboveTargets(targets);
                    int colorCode = CapabilitySlashBlade.getBladeState(entity.getMainHandItem()).map(ISlashBladeState::getColorCode).orElse(SWORD_COLOR);
                    float damage = DamageCalculator.calculateRefinedDamage(entity, 1.0F);
                    UBWData ubwData = new UBWData(swordPositions, targets, damage, colorCode);
                    activeUBW.put(player.getUUID(), ubwData);

                    for (LivingEntity target : targets) {
                        createWarningParticles(level, target.position());
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
        UBWData ubwData = activeUBW.get(uuid);
        if (ubwData != null) {
            ubwData.tickCounter++;
            ServerLevel level = (ServerLevel) player.level();
            if (ubwData.phase == 0) {
                if (ubwData.tickCounter % 2 == 0) {
                    int swordIndex = ubwData.tickCounter / 2;
                    if (swordIndex < ubwData.swordPositions.size()) {
                        Vec3 pos = ubwData.swordPositions.get(swordIndex);
                        createSwordAppearEffect(level, pos);
                    }
                }

                if (ubwData.tickCounter >= DEPLOY_DURATION) {
                    ubwData.phase = 1;
                    ubwData.tickCounter = 0;
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 1.0F);
                }
            } else if (ubwData.phase == 1) {
                if (ubwData.tickCounter % 2 == 0 && ubwData.swordsFired < ubwData.swordPositions.size()) {
                    dropSword(player, ubwData, level);
                    ubwData.swordsFired++;
                }

                if (ubwData.tickCounter >= FIRE_DURATION || ubwData.swordsFired >= ubwData.swordPositions.size()) {
                    activeUBW.remove(uuid);
                }
            }
        }
    }

    private static List<Vec3> calculateSwordPositionsAboveTargets(List<LivingEntity> targets) {
        List<Vec3> positions = new ArrayList<>();

        for (LivingEntity target : targets) {
            int swordsPerTarget = SWORD_COUNT / Math.max(targets.size(), 1);

            for (int i = 0; i < swordsPerTarget; i++) {
                double offsetX = (Math.random() - 0.5) * 1.5;
                double offsetZ = (Math.random() - 0.5) * 1.5;
                Vec3 pos = new Vec3(target.getX() + offsetX, target.getY() + SWORD_HEIGHT, target.getZ() + offsetZ);
                positions.add(pos);
            }
        }

        return positions;
    }

    private static List<LivingEntity> findAllTargets(LivingEntity caster, ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(20.0), entity -> entity != caster && entity.isAlive());
    }

    private static void dropSword(Player player, UBWData data, ServerLevel level) {
        if (!data.targets.isEmpty()) {
            Vec3 swordPos = data.swordPositions.get(data.swordsFired);
            EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(SBEntityTypes.SUMMONED_SWORD, level);
            sword.setPos(swordPos.x, swordPos.y, swordPos.z);
            sword.setOwner(player);
            sword.setColor(data.colorCode);
            sword.setDamage(data.baseDamage * 1.5F);
            sword.setIsCritical(true);
            sword.setPierce((byte) 1);
            sword.shoot(0.0, -1.5, 0.0, 3.0F, 0.0F);
            level.addFreshEntity(sword);
            level.sendParticles(ParticleTypes.GLOW, swordPos.x, swordPos.y, swordPos.z, 5, 0.1, 0.5, 0.1, 0.02);
        }
    }

    private static void createWarningParticles(ServerLevel level, Vec3 center) {
        for (int i = 0; i < 20; i++) {
            double angle = (Math.PI * 2) * (double) i / 20.0;
            double x = center.x + Math.cos(angle) * 2.0;
            double z = center.z + Math.sin(angle) * 2.0;
            level.sendParticles(ParticleTypes.GLOW, x, center.y + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void createSwordAppearEffect(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 5, 0.3, 0.3, 0.3, 0.1);
        level.sendParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 3, 0.2, 0.2, 0.2, 0.05);
    }

    public static void clearPlayerState(UUID playerUUID) {
        activeUBW.remove(playerUUID);
    }

    private static class UBWData {
        final List<Vec3> swordPositions;
        final List<LivingEntity> targets;
        int tickCounter;
        int phase;
        int swordsFired;
        final float baseDamage;
        final int colorCode;

        UBWData(List<Vec3> positions, List<LivingEntity> targets, float damage, int color) {
            this.swordPositions = positions;
            this.targets = targets;
            this.tickCounter = 0;
            this.phase = 0;
            this.swordsFired = 0;
            this.baseDamage = damage;
            this.colorCode = color;
        }
    }
}
