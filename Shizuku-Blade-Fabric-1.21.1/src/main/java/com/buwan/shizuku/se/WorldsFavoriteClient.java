package com.buwan.shizuku.se;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** 世界的偏爱者：客户端「Shift+Alt 开关坐标位移」与 WASD/空格/Shift 位移。 */
public final class WorldsFavoriteClient {
    private static boolean prevAlt;

    private WorldsFavoriteClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(WorldsFavoriteClient::onClientTick);
    }

    private static void onClientTick(Minecraft mc) {
        Player player = mc.player;
        if (player == null) {
            return;
        }
        long window = mc.getWindow().getWindow();

        boolean altDown = isDown(window, 342) || isDown(window, 346);
        if (altDown && !prevAlt && mc.screen == null
                && (isDown(window, 340) || isDown(window, 344))
                && WorldsFavorite.hasWorldsFavoriteStatic(player)) {
            UUID id = player.getUUID();
            boolean current = WorldsFavorite.coordinateShiftMode.getOrDefault(id, false);
            WorldsFavorite.coordinateShiftMode.put(id, !current);
        }
        prevAlt = altDown;

        UUID playerId = player.getUUID();
        boolean modeEnabled = WorldsFavorite.coordinateShiftMode.getOrDefault(playerId, false);
        if (modeEnabled && !WorldsFavorite.hasWorldsFavoriteStatic(player)) {
            WorldsFavorite.coordinateShiftMode.put(playerId, false);
            return;
        }
        if (!modeEnabled) {
            return;
        }

        double dx = 0.0;
        double dy = 0.0;
        double dz = 0.0;
        if (isDown(window, 87)) { // W
            dz += WorldsFavorite.SHIFT_SPEED;
        }
        if (isDown(window, 83)) { // S
            dz -= WorldsFavorite.SHIFT_SPEED;
        }
        if (isDown(window, 65)) { // A
            dx += WorldsFavorite.SHIFT_SPEED;
        }
        if (isDown(window, 68)) { // D
            dx -= WorldsFavorite.SHIFT_SPEED;
        }
        if (isDown(window, 32)) { // Space
            dy += WorldsFavorite.SHIFT_SPEED;
        }
        if (isDown(window, 340)) { // Left Shift
            dy -= WorldsFavorite.SHIFT_SPEED;
        }

        if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
            float rotation = player.getYRot();
            double cos = Math.cos(Math.toRadians(rotation));
            double sin = Math.sin(Math.toRadians(rotation));
            double rotatedDx = dx * cos + dz * sin;
            double rotatedDz = -dx * sin + dz * cos;
            Vec3 newPos = player.position().add(rotatedDx, dy, rotatedDz);
            var connection = mc.getConnection();
            if (connection != null) {
                connection.sendUnsignedCommand(String.format("tp @s %f %f %f", newPos.x, newPos.y, newPos.z));
            }
            player.setPos(newPos.x, newPos.y, newPos.z);
        }
    }

    private static boolean isDown(long window, int key) {
        return InputConstants.isKeyDown(window, key);
    }
}
