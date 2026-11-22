package com.buwan.shizuku.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.entity.Entity;

/**
 * 实体 UUID 黑名单：被拉黑的实体每 tick 都会被检查并强制移除
 * （名单是内存态，离开世界时 clearAll）。
 */
public class EntityBlackListHelper {
    private static final Map<UUID, Long> BLACKLIST_UUIDS = new ConcurrentHashMap<>();
    private static final long BLACKLIST_DURATION_MS = 2000L;
    private static final ScheduledExecutorService CLEANUP_SCHEDULER = new ScheduledThreadPoolExecutor(1);

    private static void cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        BLACKLIST_UUIDS.entrySet().removeIf(entry -> currentTime - entry.getValue() > 2000L);
    }

    public static boolean addToBlacklist(UUID uuid) {
        if (uuid != null) {
            BLACKLIST_UUIDS.put(uuid, System.currentTimeMillis());
        }

        return true;
    }

    public static boolean addToBlacklist(Entity entity) {
        if (entity != null) {
            BLACKLIST_UUIDS.put(entity.getUUID(), System.currentTimeMillis());
        }

        return true;
    }

    public static boolean removeFromBlacklist(UUID uuid) {
        BLACKLIST_UUIDS.remove(uuid);
        return true;
    }

    public static boolean isBlacklisted(UUID uuid) {
        if (!BLACKLIST_UUIDS.containsKey(uuid)) {
            return false;
        } else {
            long entryTime = BLACKLIST_UUIDS.get(uuid);
            boolean isExpired = System.currentTimeMillis() - entryTime > 2000L;
            if (isExpired) {
                BLACKLIST_UUIDS.remove(uuid);
                return false;
            } else {
                return true;
            }
        }
    }

    public static boolean isBlacklisted(Entity entity) {
        return entity != null && isBlacklisted(entity.getUUID());
    }

    public static void clearAll() {
        BLACKLIST_UUIDS.clear();
    }

    public static int size() {
        cleanupExpiredEntries();
        return BLACKLIST_UUIDS.size();
    }

    static {
        CLEANUP_SCHEDULER.scheduleAtFixedRate(EntityBlackListHelper::cleanupExpiredEntries, 1L, 1L, TimeUnit.SECONDS);
    }
}
