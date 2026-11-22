package com.buwan.shizuku.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S：触发「星河流转（幻影斩）」（1.21.1 Fabric payload 版）。 */
public record PhantomSlashPacket() implements CustomPacketPayload {
    public static final Type<PhantomSlashPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shizuku", "phantom_slash"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhantomSlashPacket> STREAM_CODEC =
            StreamCodec.ofMember(PhantomSlashPacket::write, PhantomSlashPacket::new);

    private PhantomSlashPacket(RegistryFriendlyByteBuf buf) {
        this();
    }

    private void write(RegistryFriendlyByteBuf buf) {
    }

    @Override
    public Type<PhantomSlashPacket> type() {
        return TYPE;
    }
}
