package com.buwan.shizuku.client;

import com.buwan.shizuku.registry.SASlashArtRegistry;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;




import com.buwan.shizuku.compat.ClientTickEvent;



public class YmsaChargeHandler {
    private static boolean isChargingYmsa = false;
    /**
     * 需要按住多少 tick 才让合成波出现（10 tick = 500 ms）。
     * 轻点右键也会让使用动作持续 1~2 tick，所以必须靠阈值区分「点」和「按住」；
     * 松手立刻收掉，不要加「连续 N tick 才关」的迟滞（那会让连点等效长按）。
     */
    private static final int CHARGE_TICKS_BEFORE_EFFECT = 10;

    
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
                CapabilitySlashBlade.BLADESTATE.maybeGet(mainHand).ifPresent(state -> {
                    boolean isYmsa = state.getSlashArts().equals(SASlashArtRegistry.Ymsa.get());
                    if (isUsing && isYmsa && usingTicks >= CHARGE_TICKS_BEFORE_EFFECT) {
                        if (!isChargingYmsa) {
                            SynthwaveShaderManager.startShader();
                            isChargingYmsa = true;
                        }
                    } else if (isChargingYmsa && !isUsing) {
                        // 松手第一个 tick 就收，避免松手后还挂着
                        SynthwaveShaderManager.stopShader();
                        isChargingYmsa = false;
                    }
                });
            }
        }
    }
}
