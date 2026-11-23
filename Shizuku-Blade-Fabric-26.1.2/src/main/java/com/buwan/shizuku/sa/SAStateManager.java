package com.buwan.shizuku.sa;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * SA 的全局状态：记录玩家所在维度（用于跨维度时重置状态），
 * 在玩家登录/登出/每 tick 时维护，避免换维度后 SA 状态泄漏。
 *
 * <p>Fabric 版由 {@code ServerPlayConnectionEvents.JOIN/DISCONNECT} 与
 * {@code ServerTickEvents.END_SERVER_TICK} 调用下面三个静态方法。</p>
 */
public class SAStateManager {
    private static final Map<UUID, Identifier> playerDimensions = new ConcurrentHashMap<>();

    public static void onPlayerLoggedIn(Player player) {
        if (player != null && !player.level().isClientSide()) {
            Identifier dimension = player.level().dimension().identifier();
            playerDimensions.put(player.getUUID(), dimension);
        }
    }

    public static void onPlayerLoggedOut(Player player) {
        if (player != null && !player.level().isClientSide()) {
            UUID uuid = player.getUUID();
            playerDimensions.remove(uuid);
            clearAllSAStates(uuid);
        }
    }

    /** 服务端每 tick 对每个玩家调用（相当于 Forge 的 PlayerTickEvent START）。 */
    public static void onPlayerTick(Player player) {
        if (player != null) {
            UUID uuid = player.getUUID();
            Identifier currentDimension = player.level().dimension().identifier();
            Identifier recordedDimension = playerDimensions.get(uuid);
            if (recordedDimension != null && !currentDimension.equals(recordedDimension)) {
                clearAllSAStates(uuid);
                playerDimensions.put(uuid, currentDimension);
            } else if (recordedDimension == null) {
                playerDimensions.put(uuid, currentDimension);
            }
        }
    }

    private static void clearAllSAStates(UUID playerUUID) {
        TornadoSlash.clearPlayerState(playerUUID);
        MoonlightSlash.clearPlayerState(playerUUID);
        TsubameGaeshi.clearPlayerState(playerUUID);
        TeleportSlash.clearPlayerState(playerUUID);
        UnlimitedBladeWorks.clearPlayerState(playerUUID);
        TimeSlash.clearPlayerState(playerUUID);
        VoidPrison.clearPlayerState(playerUUID);
        Yingsa.clearPlayerState(playerUUID);
        Ymsa.clearPlayerState(playerUUID);
    }
}
