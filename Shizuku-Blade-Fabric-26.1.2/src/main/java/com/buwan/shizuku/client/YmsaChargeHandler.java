package com.buwan.shizuku.client;

import com.buwan.shizuku.registry.SASlashArtRegistry;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;




import com.buwan.shizuku.compat.ClientTickEvent;



public class YmsaChargeHandler {
    private static boolean isChargingYmsa = false;

    
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            ItemStack mainHand = mc.player.getMainHandItem();
            if (!(mainHand.getItem() instanceof ItemSlashBlade)) {
                if (isChargingYmsa) {
                    SynthwaveShaderManager.stopShader();
                    isChargingYmsa = false;
                }
            } else {
                boolean isUsing = mc.player.isUsingItem();
                int usingTicks = mc.player.getTicksUsingItem();
                CapabilitySlashBlade.getBladeState(mainHand).ifPresent(state -> {
                    boolean isYmsa = state.getSlashArts().equals(SASlashArtRegistry.Ymsa.get());
                    if (isUsing && isYmsa && usingTicks > 0) {
                        if (!isChargingYmsa) {
                            SynthwaveShaderManager.startShader();
                            isChargingYmsa = true;
                        }
                    } else if (isChargingYmsa) {
                        SynthwaveShaderManager.stopShader();
                        isChargingYmsa = false;
                    }
                });
            }
        }
    }
}
