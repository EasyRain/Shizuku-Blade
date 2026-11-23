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

    /** 26.1 的 Recipe#assemble 不再接收 RegistryAccess，这里由服务端实例兜底提供。 */
    public static net.minecraft.core.RegistryAccess registryAccess() {
        return server != null ? server.registryAccess() : null;
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TimeStopPacket.TYPE, TimeStopPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TimeStopShaderPacket.TYPE, TimeStopShaderPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VoidPrisonPacket.TYPE, VoidPrisonPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GalaxyFieldPacket.TYPE, GalaxyFieldPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(YmsaForceRemovePacket.TYPE, YmsaForceRemovePacket.STREAM_CODEC);
        // ⚠️ 这三条 26.1.2 移植时漏掉了：payload 的 StreamCodec 再正确，只要类型没进对应方向的
        //    PayloadTypeRegistry，原版的分发 codec 在编码时就会抛 "unrecognized payload"，
        //    Fabric 再把它包成 EncoderException: Failed to encode packet ... (shizuku:xxx)。
        //    表现就是「一开解放/一放审判就被踢」。类型注册必须与接收方注册成对出现。
        PayloadTypeRegistry.clientboundPlay().register(JudgmentImpactPacket.TYPE, JudgmentImpactPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(FieldActivatePacket.TYPE, FieldActivatePacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UnleashedInputPacket.TYPE, UnleashedInputPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PhantomSlashPacket.TYPE, PhantomSlashPacket.STREAM_CODEC);

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

        // C2S：解放（审判）输入 —— 服务端仍会校验 OP（权限等级 >= 2）
        ServerPlayNetworking.registerGlobalReceiver(UnleashedInputPacket.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player != null) {
                        payload.handleServer(player);
                    }
                }));

        // C2S：幻影斩（只有手持「星河」时服务端才放行）
        ServerPlayNetworking.registerGlobalReceiver(PhantomSlashPacket.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player != null) {
                        payload.handle(player);
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
        ServerLevel level = (ServerLevel) player.level();
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
