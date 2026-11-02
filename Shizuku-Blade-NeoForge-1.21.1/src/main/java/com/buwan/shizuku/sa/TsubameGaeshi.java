package com.buwan.shizuku.sa;

import com.buwan.shizuku.util.DamageCalculator;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.slasharts.SakuraEnd;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TsubameGaeshi {
    private static final int SWALLOW_COLOR = 4286945;
    private static final int SLASH_INTERVAL = 6;
    private static final Map<UUID, SlashData> activeSlashes = new ConcurrentHashMap<>();

    public static void doTsubameGaeshi(LivingEntity entity) {
        if (!entity.level().isClientSide()) {
            if (entity instanceof Player player) {
                ServerLevel level = (ServerLevel) entity.level();
                float damage = DamageCalculator.calculateRefinedDamage(entity, 1.0F);
                int colorCode = BladeStateAccess.of(entity.getMainHandItem()).map(ISlashBladeState::getColorCode).orElse(SWALLOW_COLOR);
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5F, 0.8F);
                SlashData slashData = new SlashData(damage, colorCode);
                activeSlashes.put(player.getUUID(), slashData);
                executeSlash(player, slashData, 0);
                slashData.slashIndex++;
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel)) {
            return;
        }
        UUID uuid = player.getUUID();
        SlashData slashData = activeSlashes.get(uuid);
        if (slashData != null) {
            slashData.tickCounter++;
            if (slashData.tickCounter % SLASH_INTERVAL == 0 && slashData.slashIndex < 3) {
                executeSlash(player, slashData, slashData.slashIndex);
                slashData.slashIndex++;
            }

            if (slashData.slashIndex >= 3) {
                activeSlashes.remove(uuid);
            }
        }
    }

    private static void executeSlash(Player player, SlashData data, int slashIndex) {
        ServerLevel level = (ServerLevel) player.level();
        switch (slashIndex) {
            case 0:
                SakuraEnd.doSlash(player, 60.0F, data.colorCode, new Vec3(0.0, -0.3, 0.0), true, true, data.baseDamage, KnockBacks.toss);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 1.2F);
                break;
            case 1:
                SakuraEnd.doSlash(player, 0.0F, data.colorCode, new Vec3(0.0, 0.0, 0.0), true, true, data.baseDamage, KnockBacks.toss);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 1.0F);
                break;
            case 2:
                SakuraEnd.doSlash(player, -60.0F, data.colorCode, new Vec3(0.0, 0.3, 0.0), false, true, data.baseDamage, KnockBacks.toss);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.8F);
                break;
            default:
                break;
        }
    }

    public static void clearPlayerState(UUID playerUUID) {
        activeSlashes.remove(playerUUID);
    }

    private static class SlashData {
        int tickCounter = 0;
        int slashIndex = 0;
        final float baseDamage;
        final int colorCode;

        SlashData(float damage, int color) {
            this.baseDamage = damage;
            this.colorCode = color;
        }
    }
}
