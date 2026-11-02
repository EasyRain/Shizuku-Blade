package com.buwan.shizuku.network;

import com.buwan.shizuku.sa.StarRiverSlash;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：左键空挥触发星河流星斩（仅 mosmicblade）。
 */
public class PhantomSlashPacket implements CustomPacketPayload {

    public static final Type<PhantomSlashPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shizuku", "phantom_slash"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhantomSlashPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {
            }, buf -> new PhantomSlashPacket());

    @Override
    public Type<PhantomSlashPacket> type() {
        return TYPE;
    }

    public static void handle(PhantomSlashPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player != null) {
                boolean mosmic = BladeStateAccess.of(player.getMainHandItem())
                        .map(s -> "item.shizuku.mosmicblade".equals(s.getTranslationKey())).orElse(false);
                if (mosmic) {
                    StarRiverSlash.firePhantomSlash(player);
                }
            }
        });
    }
}
