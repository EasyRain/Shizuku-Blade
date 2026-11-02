package com.buwan.shizuku.sa;

import com.buwan.shizuku.shizuku;
import com.buwan.shizuku.util.DamageCalculator;
import mods.flammpfeil.slashblade.init.SBEntityTypes;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.slasharts.Drive;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class Shizukusa {
    // 音效资源位置数组（直接使用注册名）
    private static final ResourceLocation[] LEISA_SOUNDS = {
            shizuku.LEISA_SOUND1.getId(),
            shizuku.LEISA_SOUND2.getId(),
            shizuku.LEISA_SOUND3.getId()
    };

    public static void doSlash(LivingEntity playerIn, float speed) {
        int colorCode = CapabilitySlashBlade.BLADESTATE.maybeGet(playerIn.getMainHandItem())
                .map(ISlashBladeState::getColorCode).orElse(0xFF3333FF);
        doSlash(playerIn, speed, colorCode, true); // 默认播放音效
    }

    public static void doSlash(LivingEntity playerIn, float speed, int colorCode, boolean playSound) {
        if (playerIn.level().isClientSide()) return;

        // 音效播放逻辑（优化：使用资源位置直接获取）
        if (playSound) {
            RandomSource random = playerIn.getRandom();
            ResourceLocation soundLoc = LEISA_SOUNDS[random.nextInt(LEISA_SOUNDS.length)];
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundLoc);

            if (sound != null) {
                playerIn.level().playSound(null,
                        playerIn.getX(),
                        playerIn.getY(),
                        playerIn.getZ(),
                        sound,
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F + (random.nextFloat() - 0.5f) * 0.2f // 随机音高微调
                );
            }
        }

        CapabilitySlashBlade.BLADESTATE.maybeGet(playerIn.getMainHandItem()).ifPresent((state) -> {
            Level worldIn = playerIn.level();
            RandomSource random = playerIn.getRandom();

            // 1. 创建三个Drive实体
            Vec3 lookAngle = playerIn.getLookAngle();
            for (int i = -1; i <= 1; i++) {
                float angle = i * 120.0f;

                EntityDrive drive = Drive.doSlash(playerIn,
                        angle * 0.5f,
                        40,
                        new Vec3(0, 0.5, 1).scale(0.8 + random.nextFloat() * 0.4),
                        true,
                        6.0,
                        speed * (0.9f + random.nextFloat() * 0.2f)
                );

                if (drive != null) {
                    drive.setSilent(true);
                    float refinedDamage = DamageCalculator.calculateRefinedDamage(playerIn, 1.0F);
                    drive.setDamage(refinedDamage);
                }
            }

            // 2. 创建JudgementCut效果
            doFocusedJudgementCut(playerIn, 18.0f, 160.0f);
        });
    }

    // 聚焦版JudgementCut
    private static void doFocusedJudgementCut(LivingEntity owner, float range, float angle) {
        Level level = owner.level();
        ItemStack stack = owner.getMainHandItem();
        Vec3 look = owner.getLookAngle().normalize();

        List<Entity> founds = new ArrayList<>();
        for (Entity entity : level.getEntities(owner, owner.getBoundingBox().inflate(range))) {
            if (!(entity instanceof LivingEntity)) continue;

            Vec3 toTarget = entity.position().subtract(owner.position()).normalize();
            float targetAngle = (float) Math.toDegrees(Math.acos(look.dot(toTarget)));
            if (targetAngle <= angle / 2) {
                founds.add(entity);
            }
        }

        for (Entity entity : founds) {
            ((LivingEntity) entity).addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 8));

            EntityJudgementCut judgementCut = new EntityJudgementCut(SBEntityTypes.JUDGEMENT_CUT, level);
            judgementCut.setPos(entity.getX(), entity.getY(), entity.getZ());
            judgementCut.setOwner(owner);
            judgementCut.setSilent(true);

            CapabilitySlashBlade.BLADESTATE.maybeGet(stack)
                    .ifPresent(state -> judgementCut.setColor(state.getColorCode()));

            float rank = CapabilityConcentrationRank.RANK_POINT.maybeGet(owner)
                    .map(r -> r.getRankLevel(level.getGameTime())).orElse(0.0F);
            judgementCut.setRank(rank);

            level.addFreshEntity(judgementCut);
        }
    }
}
