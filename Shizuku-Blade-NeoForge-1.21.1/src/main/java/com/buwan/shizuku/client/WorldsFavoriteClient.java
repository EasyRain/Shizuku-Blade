package com.buwan.shizuku.client;

import com.buwan.shizuku.se.WorldsFavorite;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * 「世界的偏爱者」里只属于客户端的两块逻辑：Shift 切换坐标位移、以及位移本身。
 *
 * <p>这两段原来写在 {@link WorldsFavorite} 里，但那个类在**通用注册表**里注册，
 * 专用服务器加载它时会因为引用 {@code Minecraft}/{@code LocalPlayer} 抛
 * {@code Attempted to load class ... for invalid dist DEDICATED_SERVER}。
 * 1.21 NeoForge 没有 {@code DistExecutor}，所以拆到客户端专属类里，靠 {@code @EventBusSubscriber(Dist.CLIENT)} 只在客户端注册。</p>
 */
@EventBusSubscriber(modid = "shizuku", value = Dist.CLIENT)
public final class WorldsFavoriteClient {
    private static final Map<UUID, Boolean> coordinateShiftMode = new ConcurrentHashMap<>();
    private static final double SHIFT_SPEED = 0.5;

    private WorldsFavoriteClient() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        if (!WorldsFavorite.hasWorldsFavoriteStatic(mc.player)) return;

        int keyCode = event.getKey();
        if ((keyCode == 342 || keyCode == 346) && event.getAction() == 1) {
            long window = mc.getWindow().getWindow();
            boolean shiftDown = InputConstants.isKeyDown(window, 340) || InputConstants.isKeyDown(window, 344);
            if (shiftDown) {
                UUID playerId = mc.player.getUUID();
                boolean currentMode = coordinateShiftMode.getOrDefault(playerId, false);
                coordinateShiftMode.put(playerId, !currentMode);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCoordinateShiftTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        UUID playerId = player.getUUID();
        boolean modeEnabled = coordinateShiftMode.getOrDefault(playerId, false);
        if (modeEnabled && !WorldsFavorite.hasWorldsFavoriteStatic(player)) {
            coordinateShiftMode.put(playerId, false);
            return;
        }
        if (!modeEnabled) return;

        double dx = 0.0;
        double dy = 0.0;
        double dz = 0.0;
        long window = mc.getWindow().getWindow();
        if (InputConstants.isKeyDown(window, 87)) dz += SHIFT_SPEED;
        if (InputConstants.isKeyDown(window, 83)) dz -= SHIFT_SPEED;
        if (InputConstants.isKeyDown(window, 65)) dx += SHIFT_SPEED;
        if (InputConstants.isKeyDown(window, 68)) dx -= SHIFT_SPEED;
        if (InputConstants.isKeyDown(window, 32)) dy += SHIFT_SPEED;
        if (InputConstants.isKeyDown(window, 340)) dy -= SHIFT_SPEED;

        if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
            float rotation = player.getYRot();
            double cos = Math.cos(Math.toRadians(rotation));
            double sin = Math.sin(Math.toRadians(rotation));
            double rotatedDx = dx * cos + dz * sin;
            double rotatedDz = -dx * sin + dz * cos;
            Vec3 newPos = player.position().add(rotatedDx, dy, rotatedDz);
            var connection = mc.getConnection();
            if (connection != null) {
                connection.sendUnsignedCommand(
                        String.format("tp @s %f %f %f", newPos.x, newPos.y, newPos.z));
            }
            player.setPos(newPos.x, newPos.y, newPos.z);
        }
    }

    /** 登出时清掉位移模式，避免换存档后残留。 */
    public static void clear(UUID playerId) {
        coordinateShiftMode.remove(playerId);
    }
}
