package com.buwan.shizuku.client;

import com.buwan.shizuku.client.shader.CosmicShaders;
import com.buwan.shizuku.network.FieldActivatePacket;
import com.buwan.shizuku.network.PacketHandler;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class FieldActivateInput {
    private FieldActivateInput() {
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        // Shift + X（GLFW_KEY_X = 88，modifiers bit0 = SHIFT）松开时触发
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            if (event.getKey() == GLFW.GLFW_KEY_X) {
                if ((event.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        ItemStack held = mc.player.getMainHandItem();
                        if (held.getItem() instanceof ItemSlashBlade) {
                            String key = BladeStateAccess.of(held)
                                    .map(s -> s.getTranslationKey())
                                    .orElse("");
                            if (CosmicShaders.COSMIC_BLADE_KEY.equals(key)) {
                                PacketHandler.sendToServer(new FieldActivatePacket());
                            }
                        }
                    }
                }
            }
        }
    }
}
