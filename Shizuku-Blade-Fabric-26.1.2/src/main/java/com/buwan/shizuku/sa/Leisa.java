package com.buwan.shizuku.sa;

import com.buwan.shizuku.util.DamageCalculator;
import mods.flammpfeil.slashblade.slasharts.SakuraEnd;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class Leisa {
    private static final int PURPLE_COLOR = 10040319;
    private static final Identifier[] LEISA_SOUNDS = new Identifier[]{
            Identifier.fromNamespaceAndPath("shizuku", "leisa_sound1"),
            Identifier.fromNamespaceAndPath("shizuku", "leisa_sound2"),
            Identifier.fromNamespaceAndPath("shizuku", "leisa_sound3")
    };

    public static void doLeisaSlash(LivingEntity player) {
        if (!player.level().isClientSide()) {
            playLeisaSound(player);
            float damage = DamageCalculator.calculateRefinedDamage(player, 1.0F);
            SakuraEnd.doSlash(player, 0.0F, PURPLE_COLOR, new Vec3(0.0, 0.2, 0.0), true, true, damage * 0.8F, KnockBacks.cancel);
            SakuraEnd.doSlash(player, -45.0F, PURPLE_COLOR, new Vec3(0.0, 0.1, 0.0), true, true, damage, KnockBacks.cancel);
            SakuraEnd.doSlash(player, 45.0F, PURPLE_COLOR, new Vec3(0.0, 0.1, 0.0), true, true, damage, KnockBacks.cancel);
        }
    }

    private static void playLeisaSound(LivingEntity player) {
        RandomSource random = player.getRandom();
        Identifier soundLoc = LEISA_SOUNDS[random.nextInt(LEISA_SOUNDS.length)];
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundLoc);
        if (sound != null) {
            player.level()
                    .playSound(
                            null,
                            BlockPos.containing(player.getX(), player.getY(), player.getZ()),
                            sound,
                            SoundSource.PLAYERS,
                            1.0F,
                            1.0F + (random.nextFloat() - 0.5F) * 0.2F
                    );
        }
    }
}
