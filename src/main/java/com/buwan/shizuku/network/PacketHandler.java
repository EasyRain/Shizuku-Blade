package com.buwan.shizuku.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.PacketDistributor.TargetPoint;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * SimpleChannel 的注册与发送工具：sendToPlayer / sendToNearby / sendToAll。
 *
 * 频道资源名固定为 shizuku:main，收发都走这里，便于统一版本号校验。
 */
public class PacketHandler {
    private static final String PROTOCOL_VERSION = "7";
    private static int packetId = 0;
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("shizuku", "main"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals
    );

    public static void register() {
        INSTANCE.messageBuilder(TimeStopPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(TimeStopPacket::encode)
            .decoder(TimeStopPacket::decode)
            .consumerMainThread(TimeStopPacket::handle)
            .add();
        INSTANCE.messageBuilder(TimeStopShaderPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(TimeStopShaderPacket::encode)
            .decoder(TimeStopShaderPacket::decode)
            .consumerMainThread(TimeStopShaderPacket::handle)
            .add();
        INSTANCE.messageBuilder(VoidPrisonPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(VoidPrisonPacket::encode)
            .decoder(VoidPrisonPacket::decode)
            .consumerMainThread(VoidPrisonPacket::handle)
            .add();
        INSTANCE.messageBuilder(YmsaForceRemovePacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(YmsaForceRemovePacket::encode)
            .decoder(YmsaForceRemovePacket::decode)
            .consumerMainThread(YmsaForceRemovePacket::handle)
            .add();
        INSTANCE.messageBuilder(GalaxyFieldPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(GalaxyFieldPacket::encode)
            .decoder(GalaxyFieldPacket::decode)
            .consumerMainThread(GalaxyFieldPacket::handle)
            .add();
        INSTANCE.messageBuilder(FieldActivatePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(FieldActivatePacket::encode)
            .decoder(FieldActivatePacket::decode)
            .consumerMainThread(FieldActivatePacket::handle)
            .add();
        INSTANCE.messageBuilder(UnleashedInputPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(UnleashedInputPacket::encode).decoder(UnleashedInputPacket::decode)
            .consumerMainThread(UnleashedInputPacket::handle).add();
        INSTANCE.messageBuilder(JudgmentImpactPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(JudgmentImpactPacket::encode)
            .decoder(JudgmentImpactPacket::decode)
            .consumerMainThread(JudgmentImpactPacket::handle)
            .add();
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToNearby(Object packet, ServerPlayer player, double radius) {
        INSTANCE.send(
            PacketDistributor.NEAR.with(() -> new TargetPoint(player.getX(), player.getY(), player.getZ(), radius, player.level().dimension())), packet
        );
    }

    public static void sendToAll(Object packet) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
    }
}
