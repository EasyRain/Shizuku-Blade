package com.buwan.shizuku.network;

import com.buwan.shizuku.client.GalaxyFieldRenderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * S2C：星河领域圆盘特效（添加/移除）。
 */
public class GalaxyFieldPacket implements CustomPacketPayload {

    public static final Type<GalaxyFieldPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shizuku", "galaxy_field"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GalaxyFieldPacket> STREAM_CODEC =
            StreamCodec.ofMember(GalaxyFieldPacket::write, GalaxyFieldPacket::new);

    private final UUID playerId;
    private final double x;
    private final double y;
    private final double z;
    private final float radius;
    private final int duration;
    private final boolean remove;

    public GalaxyFieldPacket(UUID playerId, Vec3 center, float radius, int duration) {
        this.playerId = playerId;
        this.x = center.x;
        this.y = center.y;
        this.z = center.z;
        this.radius = radius;
        this.duration = duration;
        this.remove = false;
    }

    public GalaxyFieldPacket(UUID playerId) {
        this.playerId = playerId;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        this.radius = 0.0F;
        this.duration = 0;
        this.remove = true;
    }

    private GalaxyFieldPacket(RegistryFriendlyByteBuf buf) {
        this.playerId = buf.readUUID();
        this.remove = buf.readBoolean();
        if (this.remove) {
            this.x = this.y = this.z = 0.0;
            this.radius = 0.0F;
            this.duration = 0;
        } else {
            this.x = buf.readDouble();
            this.y = buf.readDouble();
            this.z = buf.readDouble();
            this.radius = buf.readFloat();
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
            buf.writeFloat(this.radius);
            buf.writeInt(this.duration);
        }
    }

    @Override
    public Type<GalaxyFieldPacket> type() {
        return TYPE;
    }

    public static void handle(GalaxyFieldPacket msg, IPayloadContext ctx) {
        if (FMLEnvironment.dist.isClient()) {
            ctx.enqueueWork(() -> {
                if (msg.remove) {
                    GalaxyFieldRenderer.removeZone(msg.playerId);
                } else {
                    GalaxyFieldRenderer.addZone(msg.playerId, new Vec3(msg.x, msg.y, msg.z), msg.radius, msg.duration);
                }
            });
        }
    }
}
