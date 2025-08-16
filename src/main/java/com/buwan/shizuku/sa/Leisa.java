package com.buwan.shizuku.sa;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.slasharts.SakuraEnd;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import com.buwan.shizuku.*;
import net.minecraftforge.common.util.LazyOptional;
import java.util.Optional;


public class Leisa {
    // 紫色颜色代码
    private static final int PURPLE_COLOR = 0x9933FF; // ARGB格式: 紫色

    // 音效资源
    private static final ResourceLocation[] LEISA_SOUNDS = {
            new ResourceLocation("shizuku", "leisa_sound1"),
            new ResourceLocation("shizuku", "leisa_sound2"),
            new ResourceLocation("shizuku", "leisa_sound3")
    };

    public static void doLeisaSlash(LivingEntity player) {
        if (player.level().isClientSide()) return;

        // 播放随机音效
        playLeisaSound(player);

        // 获取武器伤害值 (正确实现)
        float damage = 5.0f; // 默认值
        LazyOptional<ISlashBladeState> capability = player.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE);
        if (capability.isPresent()) {
            ISlashBladeState state = capability.resolve().get();
            damage = state.getAttackAmplifier() + 1.0f;
        }

        // 1. 横斩（水平斩击） - 停留约1秒
        SakuraEnd.doSlash(player,
                0f,            // roll角度=0（水平）
                PURPLE_COLOR,  // 紫色刀光
                new Vec3(0, 0.2, 0.5), // 位置偏移
                true,           // 静音（避免重复声音）
                true,           // 暴击
                damage * 0.8f,
                KnockBacks.cancel);


        // 2. 两次斜砍（模仿SakuraEnd）
        // 第一次斜砍（右斜）
        SakuraEnd.doSlash(player,
                -45f,         // 右斜角度
                PURPLE_COLOR,
                new Vec3(0, 0.1, 0.7),
                true,
                true,
                damage,
                KnockBacks.cancel);

        // 第二次斜砍（左斜）
        SakuraEnd.doSlash(player,
                45f,          // 左斜角度
                PURPLE_COLOR,
                new Vec3(0, 0.1, 0.7),
                true,
                true,
                damage,
                KnockBacks.cancel);
    }

    // 音效播放方法
    private static void playLeisaSound(LivingEntity player) {
        RandomSource random = player.getRandom();
        ResourceLocation soundLoc = LEISA_SOUNDS[random.nextInt(LEISA_SOUNDS.length)];
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundLoc);

        if (sound != null) {
            player.level().playSound(null,
                    BlockPos.containing(player.getX(), player.getY(), player.getZ()),
                    sound,
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F + (random.nextFloat() - 0.5f) * 0.2f);
        }
    }
}