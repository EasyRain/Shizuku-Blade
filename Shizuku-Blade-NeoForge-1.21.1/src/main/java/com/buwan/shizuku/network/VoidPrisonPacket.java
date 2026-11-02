package com.buwan.shizuku.network;

import com.buwan.shizuku.client.VoidPrisonRenderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public class VoidPrisonPacket implements CustomPacketPayload {
    public static final Type<VoidPrisonPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shizuku", "void_prison"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoidPrisonPacket> STREAM_CODEC =
            CustomPacketPayload.codec(VoidPrisonPacket::write, VoidPrisonPacket::new);

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

    @Override
    public Type<VoidPrisonPacket> type() {
        return TYPE;
    }

    public static void handle(VoidPrisonPacket msg, IPayloadContext ctx) {
        if (FMLEnvironment.dist.isClient()) {
            if (msg.remove) {
                VoidPrisonRenderer.removePrisonZone(msg.playerId);
            } else {
                VoidPrisonRenderer.addPrisonZone(msg.playerId, new Vec3(msg.x, msg.y, msg.z), msg.radius, msg.duration);
            }
        }
    }
}
