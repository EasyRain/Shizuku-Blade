package com.buwan.shizuku.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** S2C：星河领域圆盘特效（添加/移除）。 */
public class GalaxyFieldPacket implements ShizukuPacket {

    public static final ResourceLocation ID = new ResourceLocation("shizuku", "galaxy_field");

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

    public GalaxyFieldPacket(FriendlyByteBuf buf) {
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

    @Override
    public void write(FriendlyByteBuf buf) {
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
    public ResourceLocation id() {
        return ID;
    }

    public UUID playerId() {
        return playerId;
    }

    public Vec3 center() {
        return new Vec3(x, y, z);
    }

    public float radius() {
        return radius;
    }

    public int duration() {
        return duration;
    }

    public boolean remove() {
        return remove;
    }
}
