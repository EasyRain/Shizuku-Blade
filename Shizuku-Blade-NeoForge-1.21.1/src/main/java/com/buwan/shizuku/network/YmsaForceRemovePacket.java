package com.buwan.shizuku.network;

import com.buwan.shizuku.client.YmsaClientErase;
import com.buwan.shizuku.client.YmsaRenderKiller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.UUID;

/**
 * 3.5.0 抹除广播包：客户端收到后从 ClientLevel 强制移除实体并标记抹除。
 * 1.21.1 用 ClientLevel.removeEntity 公共 API 替代原版的 accessor mixin 直删。
 */
public class YmsaForceRemovePacket implements CustomPacketPayload {

    public static final Type<YmsaForceRemovePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shizuku", "ymsa_force_remove"));
    public static final StreamCodec<RegistryFriendlyByteBuf, YmsaForceRemovePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public YmsaForceRemovePacket decode(RegistryFriendlyByteBuf buf) {
                    int[] ids = buf.readVarIntArray();
                    int count = buf.readVarInt();
                    UUID[] uuids = new UUID[count];
                    for (int i = 0; i < count; i++) {
                        uuids[i] = buf.readUUID();
                    }
                    int killCount = buf.readVarInt();
                    boolean[] typeKills = new boolean[killCount];
                    for (int i = 0; i < killCount; i++) {
                        typeKills[i] = buf.readBoolean();
                    }
                    return new YmsaForceRemovePacket(ids, uuids, typeKills);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, YmsaForceRemovePacket msg) {
                    buf.writeVarIntArray(msg.entityIds);
                    buf.writeVarInt(msg.entityUuids.length);
                    for (UUID uuid : msg.entityUuids) {
                        buf.writeUUID(uuid);
                    }
                    buf.writeVarInt(msg.typeKills.length);
                    for (boolean flag : msg.typeKills) {
                        buf.writeBoolean(flag);
                    }
                }
            };

    private final int[] entityIds;
    private final UUID[] entityUuids;
    private final boolean[] typeKills;

    public YmsaForceRemovePacket(int[] entityIds, UUID[] entityUuids, boolean[] typeKills) {
        this.entityIds = entityIds;
        this.entityUuids = entityUuids;
        this.typeKills = typeKills;
    }

    @Override
    public Type<YmsaForceRemovePacket> type() {
        return TYPE;
    }

    public static void handle(YmsaForceRemovePacket msg, IPayloadContext ctx) {
        if (FMLEnvironment.dist.isClient()) {
            ctx.enqueueWork(() -> {
                for (UUID uuid : msg.entityUuids) {
                    YmsaClientErase.markErased(uuid);
                }

                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.level != null) {
                    ClientLevel level = mc.level;
                    for (int i = 0; i < msg.entityIds.length; i++) {
                        int id = msg.entityIds[i];
                        Entity entity = level.getEntity(id);
                        if (entity != null) {
                            try {
                                level.removeEntity(id, Entity.RemovalReason.KILLED);
                                purgeBossTrackers(entity);
                                if (msg.typeKills != null && i < msg.typeKills.length && msg.typeKills[i]) {
                                    YmsaRenderKiller.killEntity(entity);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            });
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
