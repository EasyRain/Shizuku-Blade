package com.buwan.shizuku.client;




import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端抹除标记集（3.5.0 的 YmsaClientErase）。
 * 服务端广播 YmsaForceRemovePacket 后，客户端将目标 UUID 记录在此，作为渲染/残留实体双保险标记。
 */

public class YmsaClientErase {

    private static final Set<UUID> ERASED = ConcurrentHashMap.newKeySet();

    private YmsaClientErase() {
    }

    public static void markErased(UUID uuid) {
        ERASED.add(uuid);
    }

    public static boolean isErased(UUID uuid) {
        return ERASED.contains(uuid);
    }

    public static void clear() {
        ERASED.clear();
    }
}
