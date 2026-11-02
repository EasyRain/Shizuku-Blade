package com.buwan.shizuku.network;

import com.buwan.shizuku.sa.GalaxyField;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：Ctrl + X 切换星河领域（仅 mosmicblade）。
 */
public class FieldActivatePacket implements CustomPacketPayload {

    public static final Type<FieldActivatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shizuku", "field_activate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FieldActivatePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {
            }, buf -> new FieldActivatePacket());

    @Override
    public Type<FieldActivatePacket> type() {
        return TYPE;
    }

    public static void handle(FieldActivatePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player != null) {
                boolean mosmic = BladeStateAccess.of(player.getMainHandItem())
                        .map(s -> "item.shizuku.mosmicblade".equals(s.getTranslationKey())).orElse(false);
                if (mosmic && player.getMainHandItem().getItem() instanceof ItemSlashBlade) {
                    GalaxyField.toggleField(player);
                }
            }
        });
    }
}
