package com.buwan.shizuku.util;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 干净公共 API 实现：替代 1.20.1 的反射/Unsafe 击杀实体方案。
 */
public class EntityKillerHelper {

    private static final Map<UUID, Integer> PENDING_FORCE_REMOVALS = new ConcurrentHashMap<>();

    /**
     * 干净的击杀流程：
     * hurt(genericKill, MAX_VALUE) → 仍存活则 kill() → 仍存活则 discard()。
     */
    public static void killEntity(Entity entity, Player player) {
        if (entity == null) {
            return;
        }
        if (entity instanceof LivingEntity le) {
            DamageSource source = player != null
                    ? player.damageSources().playerAttack(player)
                    : le.damageSources().genericKill();
            le.hurt(source, Float.MAX_VALUE);
            if (le.isAlive()) {
                le.kill();
            }
            if (le.isAlive()) {
                le.discard();
            }
        } else {
            entity.discard();
        }
    }

    public static void kill(Entity entity) {
        if (entity instanceof LivingEntity le) {
            le.hurt(le.damageSources().genericKill(), Float.MAX_VALUE);
            if (le.isAlive()) {
                le.kill();
            }
            if (le.isAlive()) {
                le.discard();
            }
        } else if (entity != null) {
            entity.discard();
        }
    }

    public static void AsynchronousAttack(Entity entity, Player player) {
        if (entity instanceof LivingEntity le) {
            le.hurt(player.damageSources().playerAttack(player), Float.POSITIVE_INFINITY);
        }
    }

    public static boolean remove(Entity entity, Player player) {
        if (entity instanceof LivingEntity le) {
            le.hurt(player.damageSources().playerAttack(player), Float.MAX_VALUE);
            le.kill();
            if (le.isAlive()) {
                le.discard();
            }
        } else if (entity != null) {
            entity.discard();
        }
        return true;
    }

    public static void AsynchronousRemove(Entity entity, Player player) {
        remove(entity, player);
    }

    public static void playEntityDeathSound(Entity entity) {
        // no-op：原版击杀流程会自行播放死亡音效
    }

    public static void tickDeath(Entity target, Player attacker) {
        // no-op：不再使用后台线程 + 反射强制推进死亡动画
    }

    public static boolean killApollyon(LivingEntity target, Player attacker) {
        target.setHealth(0.0F);
        target.hurt(attacker.damageSources().playerAttack(attacker), Float.MAX_VALUE);
        if (target.isAlive()) {
            target.kill();
        }
        return true;
    }

    public static Map<UUID, Integer> getPendingForceRemovals() {
        return PENDING_FORCE_REMOVALS;
    }
}
