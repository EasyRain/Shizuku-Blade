package com.buwan.shizuku.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import com.buwan.shizuku.sa.GalaxyField;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;

/**
 * Fabric 网络层：payload 注册 + 发送。
 * 客户端接收处理（渲染器）在 client 包移植后接入。
 */
public class PacketHandler {

    private static MinecraftServer server;

    public static void setServer(@Nullable MinecraftServer s) {
        server = s;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(TimeStopPacket.TYPE, TimeStopPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(TimeStopShaderPacket.TYPE, TimeStopShaderPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(VoidPrisonPacket.TYPE, VoidPrisonPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(GalaxyFieldPacket.TYPE, GalaxyFieldPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(YmsaForceRemovePacket.TYPE, YmsaForceRemovePacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(FieldActivatePacket.TYPE, FieldActivatePacket.STREAM_CODEC);

        // C2S：Shift+X 切换星河领域（服务端校验手持 mosmicblade）
        ServerPlayNetworking.registerGlobalReceiver(FieldActivatePacket.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player == null) {
                        return;
                    }
                    boolean mosmic = CapabilitySlashBlade.getBladeState(player.getMainHandItem())
                            .map(s -> "item.shizuku.mosmicblade".equals(s.getTranslationKey())).orElse(false);
                    if (mosmic && player.getMainHandItem().getItem() instanceof ItemSlashBlade) {
                        GalaxyField.toggleField(player);
                    }
                }));
    }

    public static void sendToServer(CustomPacketPayload packet) {
        ClientPlayNetworking.send(packet);
    }

    public static void sendToPlayer(CustomPacketPayload packet, ServerPlayer player) {
        ServerPlayNetworking.send(player, packet);
    }

    /**
     * 发给准星附近（含施法者本人）——对应 Forge {@code PacketDistributor.NEAR} 的语义。
     * 注意：NeoForge 版第二参数是 excluded，这里直接遍历，不含排除语义。
     */
    public static void sendToNearby(CustomPacketPayload packet, ServerPlayer player, double radius) {
        ServerLevel level = player.serverLevel();
        for (ServerPlayer target : level.getServer().getPlayerList().getPlayers()) {
            if (target.level() != level) {
                continue;
            }
            if (target.distanceToSqr(player) <= radius * radius) {
                ServerPlayNetworking.send(target, packet);
            }
        }
    }

    public static void sendToAll(CustomPacketPayload packet) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, packet);
        }
    }
}
