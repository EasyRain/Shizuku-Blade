package com.buwan.shizuku.client;

import com.buwan.shizuku.registry.SASlashArtRegistry;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(value = Dist.CLIENT)
public class YmsaChargeHandler {
    private static boolean isChargingYmsa = false;

    @SubscribeEvent
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
                BladeStateAccess.of(mainHand).ifPresent(state -> {
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
