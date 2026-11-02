package com.buwan.shizuku.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S：Shift + X 切换星河领域（仅 mosmicblade）。 */
public class FieldActivatePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FieldActivatePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("shizuku", "field_activate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FieldActivatePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {
            }, buf -> new FieldActivatePacket());

    @Override
    public CustomPacketPayload.Type<FieldActivatePacket> type() {
        return TYPE;
    }
}
