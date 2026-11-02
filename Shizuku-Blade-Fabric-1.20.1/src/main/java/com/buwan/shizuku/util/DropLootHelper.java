package com.buwan.shizuku.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

public class DropLootHelper {

    public static void dropLoot(Entity entity, Player player, boolean forceAllDrops) {
        Level level = entity.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        DamageSource damageSource = level.damageSources().playerAttack(player);
        if (!forceAllDrops) {
            if (entity instanceof LivingEntity le) {
                ExperienceOrb.award(serverLevel, entity.position(), le.getExperienceReward(serverLevel, player));
            }
        } else {
            forceDropAllLoot(entity, player, damageSource, serverLevel);
        }
    }

    private static void forceDropAllLoot(Entity entity, Player player, DamageSource damageSource, ServerLevel serverLevel) {
        if (entity instanceof LivingEntity livingEntity) {
            // 通过原版 die() 触发完整死亡掉落（掉落表 + 经验 + 装备）
            if (!livingEntity.isDeadOrDying()) {
                livingEntity.die(damageSource);
            }

            ExperienceOrb.award(serverLevel, entity.position(), livingEntity.getExperienceReward(serverLevel, player));

            for (ItemStack item : livingEntity.getHandSlots()) {
                if (!item.isEmpty()) {
                    entity.spawnAtLocation(item);
                }
            }
        }
    }

    private static ResourceKey<LootTable> getLootTableLocation(Entity entity) {
        return entity instanceof LivingEntity livingEntity ? livingEntity.getLootTable() : null;
    }

    public static void dropItemAt(Level level, Vec3 pos, ItemStack stack) {
        if (!stack.isEmpty() && level instanceof ServerLevel) {
            ItemStack droppedStack = stack.copy();
            level.addFreshEntity(new ItemEntity(level, pos.x, pos.y, pos.z, droppedStack));
        }
    }

    public static void dropExperience(Level level, Vec3 pos, int amount) {
        if (amount > 0 && level instanceof ServerLevel) {
            ExperienceOrb.award((ServerLevel) level, pos, amount);
        }
    }
}
