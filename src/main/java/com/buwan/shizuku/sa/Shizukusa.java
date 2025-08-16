package com.buwan.shizuku.sa;

import com.buwan.shizuku.shizuku;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRankCapabilityProvider;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.slasharts.Drive;
import net.minecraft.util.Mth;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

public class Shizukusa {
    // 音效资源位置数组（直接使用注册名）
    private static final ResourceLocation[] LEISA_SOUNDS = {
            shizuku.LEISA_SOUND1.getId(),
            shizuku.LEISA_SOUND2.getId(),
            shizuku.LEISA_SOUND3.getId()
    };

    public static void doSlash(LivingEntity playerIn, float speed) {
        int colorCode = playerIn.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                .map(ISlashBladeState::getColorCode).orElse(0xFF3333FF);
        doSlash(playerIn, speed, colorCode, true); // 默认播放音效
    }

    public static void doSlash(LivingEntity playerIn, float speed, int colorCode, boolean playSound) {
        if (playerIn.level().isClientSide()) return;

        // 音效播放逻辑（优化：使用资源位置直接获取）
        if (playSound) {
            RandomSource random = playerIn.getRandom();
            ResourceLocation soundLoc = LEISA_SOUNDS[random.nextInt(LEISA_SOUNDS.length)];
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundLoc);

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

        playerIn.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).ifPresent((state) -> {
            Level worldIn = playerIn.level();
            RandomSource random = playerIn.getRandom();

            // 1. 创建三个Drive实体
            Vec3 lookAngle = playerIn.getLookAngle();
            for (int i = -1; i <= 1; i++) {
                float angle = i * 120.0f;
                Vec3 rotatedLook = rotateY(lookAngle, angle);

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
                }
            }

            // 2. 创建JudgementCut效果
            doFocusedJudgementCut(playerIn, 18.0f, 160.0f);
        });
    }

    // 向量旋转辅助方法
    private static Vec3 rotateY(Vec3 vec, float angleDegrees) {
        float angleRad = angleDegrees * Mth.DEG_TO_RAD;
        float cos = Mth.cos(angleRad);
        float sin = Mth.sin(angleRad);
        return new Vec3(
                vec.x * cos - vec.z * sin,
                vec.y,
                vec.x * sin + vec.z * cos
        );
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

            EntityJudgementCut judgementCut = new EntityJudgementCut(SlashBlade.RegistryEvents.JudgementCut, level);
            judgementCut.setPos(entity.getX(), entity.getY(), entity.getZ());
            judgementCut.setOwner(owner);
            judgementCut.setSilent(true);

            stack.getCapability(ItemSlashBlade.BLADESTATE)
                    .ifPresent(state -> judgementCut.setColor(state.getColorCode()));

            owner.getCapability(ConcentrationRankCapabilityProvider.RANK_POINT)
                    .ifPresent(rank -> judgementCut.setRank(rank.getRankLevel(level.getGameTime())));

            level.addFreshEntity(judgementCut);
        }
    }
}