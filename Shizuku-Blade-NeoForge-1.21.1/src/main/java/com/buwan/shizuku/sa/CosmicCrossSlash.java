package com.buwan.shizuku.sa;

import com.buwan.shizuku.util.DamageCalculator;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.slasharts.SakuraEnd;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class CosmicCrossSlash {
    private static final int COSMIC_COLOR = 9662683;

    public static void doCosmicCrossSlash(LivingEntity entity) {
        if (!entity.level().isClientSide()) {
            if (entity instanceof Player player) {
                ServerLevel level = (ServerLevel) entity.level();
                int colorCode = BladeStateAccess.of(entity.getMainHandItem()).map(ISlashBladeState::getColorCode).orElse(COSMIC_COLOR);
                float finalDamage = DamageCalculator.calculateRefinedDamage(entity, 1.0F);
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 1.5F);
                createCosmicAura(level, entity.position(), entity.getViewVector(1.0F));
                SakuraEnd.doSlash(player, 45.0F, colorCode, new Vec3(0.0, 0.2, 0.8), true, true, finalDamage * 0.6F, KnockBacks.cancel);
                Vec3 frontPos = entity.position().add(entity.getViewVector(1.0F).scale(3.0));
                createStarburstParticles(level, frontPos);
                level.getServer().execute(() -> {
                    if (entity.isAlive() && !entity.isRemoved()) {
                        SakuraEnd.doSlash(player, -45.0F, colorCode, new Vec3(0.0, 0.2, 0.8), false, true, finalDamage * 0.6F, KnockBacks.smash);
                        createCrossExplosion(level, frontPos);
                        level.playSound(null, frontPos.x, frontPos.y, frontPos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.5F);
                    }
                });
            }
        }
    }

    private static void createCosmicAura(ServerLevel level, Vec3 pos, Vec3 lookAngle) {
        for (int i = 0; i < 30; i++) {
            double angle = (Math.PI * 2) * (double) i / 30.0;
            double radius = 1.5;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            double y = pos.y + 1.0 + Math.sin(angle * 4.0) * 0.5;
            level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0.0, 0.0, 0.0, 0.02);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 2, 0.1, 0.1, 0.1, 0.5);
        }

        for (int i = 0; i < 20; i++) {
            double distance = (double) i * 0.3;
            Vec3 particlePos = pos.add(lookAngle.scale(distance)).add(0.0, 1.0, 0.0);
            level.sendParticles(ParticleTypes.SONIC_BOOM, particlePos.x, particlePos.y, particlePos.z, 1, 0.2, 0.2, 0.2, 0.02);
        }
    }

    private static void createStarburstParticles(ServerLevel level, Vec3 center) {
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2) * (double) i / 8.0;

            for (int j = 0; j < 10; j++) {
                double distance = (double) j * 0.3;
                double x = center.x + Math.cos(angle) * distance;
                double z = center.z + Math.sin(angle) * distance;
                double y = center.y + 1.0;
                level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 1, 0.0, 0.0, 0.0, 0.02);
            }
        }
    }

    private static void createCrossExplosion(ServerLevel level, Vec3 center) {
        for (int i = -5; i <= 5; i++) {
            double offset = (double) i * 0.5;
            Vec3 pos1 = center.add(offset, 1.0 + (double) Math.abs(i) * 0.1, offset);
            level.sendParticles(ParticleTypes.FLASH, pos1.x, pos1.y, pos1.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.ENCHANT, pos1.x, pos1.y, pos1.z, 3, 0.1, 0.1, 0.1, 0.05);
            Vec3 pos2 = center.add(offset, 1.0 + (double) Math.abs(i) * 0.1, -offset);
            level.sendParticles(ParticleTypes.FLASH, pos2.x, pos2.y, pos2.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.ENCHANT, pos2.x, pos2.y, pos2.z, 3, 0.1, 0.1, 0.1, 0.05);
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1.0, center.z, 1, 0.0, 0.0, 0.0, 0.0);

        for (int i = 0; i < 40; i++) {
            double angle = (Math.PI * 2) * (double) i / 40.0;
            double radius = 2.0;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, center.y + 1.0, z, 3, 0.1, 0.3, 0.1, 0.1);
        }
    }
}
