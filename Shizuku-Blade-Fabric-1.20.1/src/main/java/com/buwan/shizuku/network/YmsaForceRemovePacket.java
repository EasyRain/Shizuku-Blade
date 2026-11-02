package com.buwan.shizuku.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** 3.5.0 抹除广播包（S2C）：客户端收到后强制移除实体并标记抹除。 */
public class YmsaForceRemovePacket implements ShizukuPacket {

    public static final ResourceLocation ID = new ResourceLocation("shizuku", "ymsa_force_remove");

    private final int[] entityIds;
    private final UUID[] entityUuids;
    private final boolean[] typeKills;

    public YmsaForceRemovePacket(int[] entityIds, UUID[] entityUuids, boolean[] typeKills) {
        this.entityIds = entityIds;
        this.entityUuids = entityUuids;
        this.typeKills = typeKills;
    }

    public YmsaForceRemovePacket(FriendlyByteBuf buf) {
        this.entityIds = buf.readVarIntArray();
        int count = buf.readVarInt();
        this.entityUuids = new UUID[count];
        for (int i = 0; i < count; i++) {
            this.entityUuids[i] = buf.readUUID();
        }
        int killCount = buf.readVarInt();
        this.typeKills = new boolean[killCount];
        for (int i = 0; i < killCount; i++) {
            this.typeKills[i] = buf.readBoolean();
        }
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarIntArray(this.entityIds);
        buf.writeVarInt(this.entityUuids.length);
        for (UUID uuid : this.entityUuids) {
            buf.writeUUID(uuid);
        }
        buf.writeVarInt(this.typeKills.length);
        for (boolean flag : this.typeKills) {
            buf.writeBoolean(flag);
        }
    }

    @Override
    public ResourceLocation id() {
        return ID;
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
}
