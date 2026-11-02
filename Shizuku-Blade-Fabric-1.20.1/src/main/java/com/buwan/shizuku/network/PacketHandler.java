package com.buwan.shizuku.network;

import com.buwan.shizuku.sa.GalaxyField;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 1.20.1 网络层：按 {@link ShizukuPacket#id()} 的通道名收发（Fabric 旧通道 API）。
 * 客户端接收注册在 {@code ShizukuFabricClient#registerClientReceivers()}。
 */
public class PacketHandler {

    private static MinecraftServer server;

    public static void setServer(@Nullable MinecraftServer s) {
        server = s;
    }

    /** 供创造页/配方等处取数据包注册表（服务端实例存在时）。 */
    public static net.minecraft.core.RegistryAccess registryAccess() {
        return server != null ? server.registryAccess() : null;
    }

    /** 服务端接收（common init 调用）。 */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(FieldActivatePacket.ID,
                (srv, player, handler, buf, sender) -> srv.execute(() -> {
                    boolean mosmic = CapabilitySlashBlade.BLADESTATE.maybeGet(player.getMainHandItem())
                            .map(s -> "item.shizuku.mosmicblade".equals(s.getTranslationKey())).orElse(false);
                    org.slf4j.LoggerFactory.getLogger("shizuku-field").info(
                            "[shizuku-field] server got activate from {} mosmic={} item={}",
                            player.getName().getString(), mosmic, player.getMainHandItem().getItem());
                    if (mosmic && player.getMainHandItem().getItem() instanceof ItemSlashBlade) {
                        GalaxyField.toggleField(player);
                    }
                }));
    }

    public static void sendToServer(ShizukuPacket packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        packet.write(buf);
        ClientPlayNetworking.send(packet.id(), buf);
    }

    public static void sendToPlayer(ShizukuPacket packet, ServerPlayer player) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        packet.write(buf);
        ServerPlayNetworking.send(player, packet.id(), buf);
    }

    public static void sendToNearby(ShizukuPacket packet, ServerPlayer player, double radius) {
        ServerLevel level = player.serverLevel();
        for (ServerPlayer target : level.getServer().getPlayerList().getPlayers()) {
            if (target.level() != level) {
                continue;
            }
            if (target.distanceToSqr(player) <= radius * radius) {
                sendToPlayer(packet, target);
            }
        }
    }

    public static void sendToAll(ShizukuPacket packet) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToPlayer(packet, player);
        }
    }
}
