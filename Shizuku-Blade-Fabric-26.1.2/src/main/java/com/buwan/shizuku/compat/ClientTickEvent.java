package com.buwan.shizuku.compat;

/** NeoForge {@code ClientTickEvent.Post} 的兼容替身（Fabric 用 ClientTickEvents 派发）。 */
public final class ClientTickEvent {
    private ClientTickEvent() {
    }

    public static final class Post {
    }
}
