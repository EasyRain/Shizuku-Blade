package com.buwan.shizuku.client;

import com.buwan.shizuku.network.PacketHandler;
import com.buwan.shizuku.network.PhantomSlashPacket;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「星河流转（幻影斩）」的左键输入：拦截 InteractionKeyMappingTriggered，
 * 按下左键且满足条件时改发 PhantomSlashPacket。
 */
@OnlyIn(Dist.CLIENT)
public final class PhantomSlashInput {
    private PhantomSlashInput() {
    }

    @SubscribeEvent
    public static void onLeftClick(InteractionKeyMappingTriggered event) {
        if (event.isAttack()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                if (mc.hitResult != null && mc.hitResult.getType() == Type.MISS) {
                    ItemStack held = mc.player.getMainHandItem();
                    if (held.getItem() instanceof ItemSlashBlade) {
                        String key = held.getCapability(ItemSlashBlade.BLADESTATE).map(ISlashBladeState::getTranslationKey).orElse("");
                        if ("item.shizuku.mosmicblade".equals(key)) {
                            event.setCanceled(true);
                            mc.player.swing(InteractionHand.MAIN_HAND);
                            PacketHandler.INSTANCE.sendToServer(new PhantomSlashPacket());
                        }
                    }
                }
            }
        }
    }
}
