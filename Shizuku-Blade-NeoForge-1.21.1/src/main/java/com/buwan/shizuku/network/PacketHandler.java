package com.buwan.shizuku.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(TimeStopPacket.TYPE, TimeStopPacket.STREAM_CODEC, TimeStopPacket::handle);
        registrar.playToClient(TimeStopShaderPacket.TYPE, TimeStopShaderPacket.STREAM_CODEC, TimeStopShaderPacket::handle);
        registrar.playToClient(VoidPrisonPacket.TYPE, VoidPrisonPacket.STREAM_CODEC, VoidPrisonPacket::handle);
        registrar.playToClient(SynthwaveShaderPacket.TYPE, SynthwaveShaderPacket.STREAM_CODEC, SynthwaveShaderPacket::handle);
        registrar.playToClient(YmsaForceRemovePacket.TYPE, YmsaForceRemovePacket.STREAM_CODEC, YmsaForceRemovePacket::handle);
        // 3.6.0：星河领域特效（S2C）
        registrar.playToClient(GalaxyFieldPacket.TYPE, GalaxyFieldPacket.STREAM_CODEC, GalaxyFieldPacket::handle);
        // 3.6.0：星河领域开关（C2S）
        // 注意：PhantomSlashPacket/PhantomSlashInput 为原作者取消左键幻影斩后遗留的代码，原版不注册，这里保持一致
        registrar.playToServer(FieldActivatePacket.TYPE, FieldActivatePacket.STREAM_CODEC, FieldActivatePacket::handle);
    }

    public static void sendToServer(Object packet) {
        PacketDistributor.sendToServer((CustomPacketPayload) packet);
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, (CustomPacketPayload) packet);
    }

    public static void sendToNearby(Object packet, ServerPlayer player, double radius) {
        // 注意：第二参数是 excluded。原版 Forge 的 PacketDistributor.NEAR 会包含施法者本人；
        // 若把 player 传为 excluded，单人游戏中包将发给零人，客户端特效全部失效。
        PacketDistributor.sendToPlayersNear(player.serverLevel(), null,
                player.getX(), player.getY(), player.getZ(), radius, (CustomPacketPayload) packet);
    }

    public static void sendToAll(Object packet) {
        PacketDistributor.sendToAllPlayers((CustomPacketPayload) packet);
    }
}
