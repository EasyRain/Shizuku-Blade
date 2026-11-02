package com.buwan.shizuku.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** S2C：时间停止后处理着色器开关。 */
public class TimeStopShaderPacket implements ShizukuPacket {
    public static final ResourceLocation ID = new ResourceLocation("shizuku", "time_stop_shader");

    private final boolean activate;
    private final float strength;

    public TimeStopShaderPacket(boolean activate, float strength) {
        this.activate = activate;
        this.strength = strength;
    }

    public TimeStopShaderPacket(boolean activate) {
        this(activate, activate ? 1.0F : 0.0F);
    }

    public TimeStopShaderPacket(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readFloat());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.activate);
        buf.writeFloat(this.strength);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    public boolean activate() {
        return activate;
    }

    public float strength() {
        return strength;
    }
}
