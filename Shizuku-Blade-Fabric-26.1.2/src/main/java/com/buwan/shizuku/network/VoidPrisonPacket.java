package com.buwan.shizuku.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** S2C：虚空囚笼领域（添加/移除）。 */
public class VoidPrisonPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VoidPrisonPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("shizuku", "void_prison"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoidPrisonPacket> STREAM_CODEC =
            StreamCodec.ofMember(VoidPrisonPacket::write, VoidPrisonPacket::new);

    private final UUID playerId;
    private final double x;
    private final double y;
    private final double z;
    private final double radius;
    private final int duration;
    private final boolean remove;

    public VoidPrisonPacket(UUID playerId, Vec3 center, double radius, int duration) {
        this.playerId = playerId;
        this.x = center.x;
        this.y = center.y;
        this.z = center.z;
        this.radius = radius;
        this.duration = duration;
        this.remove = false;
    }

    public VoidPrisonPacket(UUID playerId) {
        this.playerId = playerId;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        this.radius = 0.0;
        this.duration = 0;
        this.remove = true;
    }

    private VoidPrisonPacket(RegistryFriendlyByteBuf buf) {
        this.playerId = buf.readUUID();
        this.remove = buf.readBoolean();
        if (this.remove) {
            this.x = this.y = this.z = 0.0;
            this.radius = 0.0;
            this.duration = 0;
        } else {
            this.x = buf.readDouble();
            this.y = buf.readDouble();
            this.z = buf.readDouble();
            this.radius = buf.readDouble();
            this.duration = buf.readInt();
        }
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.playerId);
        buf.writeBoolean(this.remove);
        if (!this.remove) {
            buf.writeDouble(this.x);
            buf.writeDouble(this.y);
            buf.writeDouble(this.z);
            buf.writeDouble(this.radius);
            buf.writeInt(this.duration);
        }
    }

    public UUID playerId() {
        return playerId;
    }

    public Vec3 center() {
        return new Vec3(x, y, z);
    }

    public double radius() {
        return radius;
    }

    public int duration() {
        return duration;
    }

    public boolean remove() {
        return remove;
    }

    @Override
    public CustomPacketPayload.Type<VoidPrisonPacket> type() {
        return TYPE;
    }
}
