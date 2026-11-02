package com.buwan.shizuku.network;

import com.buwan.shizuku.client.SynthwaveShaderManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SynthwaveShaderPacket implements CustomPacketPayload {
    public static final Type<SynthwaveShaderPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shizuku", "synthwave_shader"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SynthwaveShaderPacket> STREAM_CODEC =
            CustomPacketPayload.codec(SynthwaveShaderPacket::write, SynthwaveShaderPacket::new);

    private final boolean activate;

    public SynthwaveShaderPacket(boolean activate) {
        this.activate = activate;
    }

    private SynthwaveShaderPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(this.activate);
    }

    @Override
    public Type<SynthwaveShaderPacket> type() {
        return TYPE;
    }

    public static void handle(SynthwaveShaderPacket msg, IPayloadContext ctx) {
        if (FMLEnvironment.dist.isClient()) {
            if (msg.activate) {
                SynthwaveShaderManager.startShader();
            } else {
                SynthwaveShaderManager.stopShader();
            }
        }
    }
}
