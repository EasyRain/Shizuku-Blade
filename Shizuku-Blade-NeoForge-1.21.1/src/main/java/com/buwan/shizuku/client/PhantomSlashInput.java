package com.buwan.shizuku.client;

import com.buwan.shizuku.client.shader.CosmicShaders;
import com.buwan.shizuku.network.PacketHandler;
import com.buwan.shizuku.network.PhantomSlashPacket;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;

@OnlyIn(Dist.CLIENT)
public final class PhantomSlashInput {
    private PhantomSlashInput() {
    }

    @SubscribeEvent
    public static void onLeftClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.MISS) {
                    ItemStack held = mc.player.getMainHandItem();
                    if (held.getItem() instanceof ItemSlashBlade) {
                        String key = BladeStateAccess.of(held)
                                .map(s -> s.getTranslationKey())
                                .orElse("");
                        if (CosmicShaders.COSMIC_BLADE_KEY.equals(key)) {
                            event.setCanceled(true);
                            mc.player.swing(InteractionHand.MAIN_HAND);
                            PacketHandler.sendToServer(new PhantomSlashPacket());
                        }
                    }
                }
            }
        }
    }
}
