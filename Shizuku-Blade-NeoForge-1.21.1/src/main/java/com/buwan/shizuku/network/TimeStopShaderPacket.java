package com.buwan.shizuku.network;

import com.buwan.shizuku.client.TimeStopShaderManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class TimeStopShaderPacket implements CustomPacketPayload {
    public static final Type<TimeStopShaderPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shizuku", "time_stop_shader"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TimeStopShaderPacket> STREAM_CODEC =
            CustomPacketPayload.codec(TimeStopShaderPacket::write, TimeStopShaderPacket::new);

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

    @Override
    public Type<TimeStopShaderPacket> type() {
        return TYPE;
    }

    public static void handle(TimeStopShaderPacket msg, IPayloadContext ctx) {
        if (FMLEnvironment.dist.isClient()) {
            TimeStopShaderManager.syncFromServer(msg.activate, msg.strength);
        }
    }
}
