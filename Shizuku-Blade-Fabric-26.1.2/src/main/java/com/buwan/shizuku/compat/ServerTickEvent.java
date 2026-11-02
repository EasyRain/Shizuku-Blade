package com.buwan.shizuku.compat;

/**
 * NeoForge {@code ServerTickEvent.Post} 的兼容替身。
 * 由 Fabric 的 {@code ServerTickEvents.END_SERVER_TICK} 派发。
 */
public final class ServerTickEvent {
    private ServerTickEvent() {
    }

    public static final class Post {
    }
}
