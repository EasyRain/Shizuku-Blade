package com.buwan.shizuku.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** 3.5.0 抹除广播包（S2C）：客户端收到后强制移除实体并标记抹除。 */
public class YmsaForceRemovePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<YmsaForceRemovePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("shizuku", "ymsa_force_remove"));
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

    public int[] entityIds() {
        return entityIds;
    }

    public UUID[] entityUuids() {
        return entityUuids;
    }

    public boolean[] typeKills() {
        return typeKills;
    }

    @Override
    public CustomPacketPayload.Type<YmsaForceRemovePacket> type() {
        return TYPE;
    }
}
