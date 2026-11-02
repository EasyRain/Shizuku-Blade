package com.buwan.shizuku.compat;

/** NeoForge {@code RenderGuiEvent.Post} 的兼容替身（Fabric 用 HudRenderCallback 派发）。 */
public final class RenderGuiEvent {
    private RenderGuiEvent() {
    }

    public static final class Post {
    }
}
