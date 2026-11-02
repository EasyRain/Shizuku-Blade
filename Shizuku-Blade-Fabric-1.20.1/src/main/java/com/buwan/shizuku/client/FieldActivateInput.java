package com.buwan.shizuku.client;

import com.buwan.shizuku.client.shader.CosmicShaders;
import com.buwan.shizuku.network.FieldActivatePacket;
import com.buwan.shizuku.network.PacketHandler;
import com.mojang.blaze3d.platform.InputConstants;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Shift + X 切换星河领域（仅 mosmicblade）。
 * Fabric 没有 InputEvent.Key，改为在 ClientTick 中轮询按键边沿。
 */
public final class FieldActivateInput {
    private static boolean prevXDown;

    private FieldActivateInput() {
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            prevXDown = false;
            return;
        }
        long window = mc.getWindow().getWindow();
        boolean xDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_X);
        boolean shiftDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (prevXDown && !xDown && shiftDown) {
            ItemStack held = mc.player.getMainHandItem();
            if (held.getItem() instanceof ItemSlashBlade) {
                String key = CapabilitySlashBlade.getBladeState(held)
                        .map(s -> s.getTranslationKey())
                        .orElse("");
                if (CosmicShaders.COSMIC_BLADE_KEY.equals(key)) {
                    PacketHandler.sendToServer(new FieldActivatePacket());
                }
            }
        }
        prevXDown = xDown;
    }
}
