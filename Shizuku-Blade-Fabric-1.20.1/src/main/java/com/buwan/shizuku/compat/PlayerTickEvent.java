package com.buwan.shizuku.compat;

import net.minecraft.world.entity.player.Player;

/**
 * NeoForge {@code PlayerTickEvent.Post} 的兼容替身。
 * 由 Fabric 的 {@code ServerTickEvents.END_SERVER_TICK} 逐玩家派发。
 */
public final class PlayerTickEvent {
    private PlayerTickEvent() {
    }

    public static final class Post {
        private final Player entity;

        public Post(Player entity) {
            this.entity = entity;
        }

        public Player getEntity() {
            return entity;
        }
    }
}
