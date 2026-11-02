package com.buwan.shizuku.client;

import com.buwan.shizuku.network.YmsaForceRemovePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.UUID;

/** 抹除广播的客户端处理（从 NeoForge 版的 payload handle 搬出）。 */
public final class YmsaClientReceiver {
    private YmsaClientReceiver() {
    }

    public static void handle(YmsaForceRemovePacket msg) {
        for (UUID uuid : msg.entityUuids()) {
            YmsaClientErase.markErased(uuid);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }
        ClientLevel level = mc.level;
        for (int i = 0; i < msg.entityIds().length; i++) {
            int id = msg.entityIds()[i];
            Entity entity = level.getEntity(id);
            if (entity != null) {
                try {
                    level.removeEntity(id, Entity.RemovalReason.KILLED);
                    purgeBossTrackers(entity);
                    if (msg.typeKills() != null && i < msg.typeKills().length && msg.typeKills()[i]) {
                        YmsaRenderKiller.killEntity(entity);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 清理第三方 Boss 模组的客户端追踪列表（沿用 3.5.0 的兼容性处理）。 */
    private static void purgeBossTrackers(Entity entity) {
        try {
            Class<?> handleClass = Class.forName("com.wzz.witherzilla.event.EventHandle");
            Field listField = handleClass.getDeclaredField("livingEntities");
            listField.setAccessible(true);
            if (listField.get(null) instanceof Collection<?> list) {
                list.remove(entity);
            }
        } catch (Exception ignored) {
        }

        try {
            Class<?> handleClass = Class.forName("net.wzz.chaoswither.event.EventHandle");
            Field listField = handleClass.getDeclaredField("livingEntities");
            listField.setAccessible(true);
            if (listField.get(null) instanceof Collection<?> list) {
                list.remove(entity);
            }
        } catch (Exception ignored) {
        }
    }
}
