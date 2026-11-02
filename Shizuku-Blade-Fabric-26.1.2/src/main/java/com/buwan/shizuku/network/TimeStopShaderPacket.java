package com.buwan.shizuku.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C：时间停止后处理着色器开关。 */
public class TimeStopShaderPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TimeStopShaderPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("shizuku", "time_stop_shader"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TimeStopShaderPacket> STREAM_CODEC =
            StreamCodec.ofMember(TimeStopShaderPacket::write, TimeStopShaderPacket::new);

    private final boolean activate;
    private final float strength;

    public TimeStopShaderPacket(boolean activate, float strength) {
        this.activate = activate;
        this.strength = strength;
    }

    public TimeStopShaderPacket(boolean activate) {
        this(activate, activate ? 1.0F : 0.0F);
    }

    private TimeStopShaderPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readFloat());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(this.activate);
        buf.writeFloat(this.strength);
    }

    public boolean activate() {
        return activate;
    }

    public float strength() {
        return strength;
    }

    @Override
    public CustomPacketPayload.Type<TimeStopShaderPacket> type() {
        return TYPE;
    }
}
