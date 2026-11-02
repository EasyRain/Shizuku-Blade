package com.buwan.shizuku.util;

import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 干净公共 API 实现：替代 1.20.1 的反射锁血字段方案。
 * 通过记录目标生命值并在需要时用 setHealth 修正，避免直接操作私有字段。
 */
public class HealthLockManager {

    private static final Map<UUID, LockedHealthData> lockedEntities = new ConcurrentHashMap<>();

    public static void lockEntityHealth(LivingEntity entity, float originalHealth, float lockedHealth) {
        lockedEntities.put(entity.getUUID(), new LockedHealthData(originalHealth, lockedHealth));
    }

    public static void unlockEntityHealth(LivingEntity entity) {
        lockedEntities.remove(entity.getUUID());
    }

    public static boolean isEntityLocked(LivingEntity entity) {
        return lockedEntities.containsKey(entity.getUUID());
    }

    public static LockedHealthData getLockedData(LivingEntity entity) {
        return lockedEntities.get(entity.getUUID());
    }

    public static void clearAllLocks() {
        lockedEntities.clear();
    }

    public static Set<UUID> getLockedEntityUUIDs() {
        return new HashSet<>(lockedEntities.keySet());
    }

    public static int getLockedEntityCount() {
        return lockedEntities.size();
    }

    /** 将实体生命值修正回锁定值（若当前值高于锁定值）。 */
    public static boolean checkAndFixFields(LivingEntity entity) {
        LockedHealthData lockData = getLockedData(entity);
        if (lockData == null) {
            return false;
        }
        float current = entity.getHealth();
        if (current > lockData.lockedHealth) {
            entity.setHealth(lockData.lockedHealth);
            return true;
        }
        return false;
    }

    public static class LockedHealthData {
        public final float originalHealth;
        public final float lockedHealth;
        public final long lockTime;

        public LockedHealthData(float originalHealth, float lockedHealth) {
            this.originalHealth = originalHealth;
            this.lockedHealth = lockedHealth;
            this.lockTime = System.currentTimeMillis();
        }
    }
}
