package com.buwan.shizuku.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class LootDropController {

    private static final double DEFAULT_CLEANUP_RANGE = 50.0;

    public static boolean isPlayerSneaking(Player player) {
        return player.isShiftKeyDown();
    }

    public static void cleanupDrops(Player player, double range) {
        if (player.level().isClientSide()) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos playerPos = player.blockPosition();
        AABB bounds = new AABB(
                (double) playerPos.getX() - range,
                (double) playerPos.getY() - range,
                (double) playerPos.getZ() - range,
                (double) playerPos.getX() + range,
                (double) playerPos.getY() + range,
                (double) playerPos.getZ() + range
        );

        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, bounds)) {
            drop.discard();
        }

        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, bounds)) {
            orb.discard();
        }
    }

    public static void cleanupDrops(Player player) {
        cleanupDrops(player, DEFAULT_CLEANUP_RANGE);
    }

    public static boolean conditionalCleanup(Player player, double range) {
        if (isPlayerSneaking(player)) {
            cleanupDrops(player, range);
            return true;
        }
        return false;
    }

    public static boolean conditionalCleanup(Player player) {
        return conditionalCleanup(player, DEFAULT_CLEANUP_RANGE);
    }

    public static int getDropCount(Player player, double range) {
        if (player.level().isClientSide()) {
            return 0;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos playerPos = player.blockPosition();
        AABB bounds = new AABB(
                (double) playerPos.getX() - range,
                (double) playerPos.getY() - range,
                (double) playerPos.getZ() - range,
                (double) playerPos.getX() + range,
                (double) playerPos.getY() + range,
                (double) playerPos.getZ() + range
        );
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, bounds);
        List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, bounds);
        return drops.size() + orbs.size();
    }
}
