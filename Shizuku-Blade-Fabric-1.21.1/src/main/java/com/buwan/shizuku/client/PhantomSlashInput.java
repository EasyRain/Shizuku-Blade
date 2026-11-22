package com.buwan.shizuku.client;

import com.buwan.shizuku.network.PacketHandler;
import com.buwan.shizuku.network.PhantomSlashPacket;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult.Type;

/**
 * 「星河流转（幻影斩）」的左键输入：空挥左键且手持星河之刃时改发 {@link PhantomSlashPacket}。
 *
 * <p>Forge 版拦的是 {@code InteractionKeyMappingTriggered}；Fabric 没有这个事件，
 * 改为在 {@code ClientTickEvents.END_CLIENT_TICK} 里检测「左键刚按下 + 空挥」，
 * 由 {@code ShizukuFabricClient} 调 {@link #tick()}。</p>
 */
public final class PhantomSlashInput {
    private static boolean wasAttackDown;

    private PhantomSlashInput() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null) {
            return;
        }
        boolean attackDown = mc.options.keyAttack.isDown();
        boolean justPressed = attackDown && !wasAttackDown;
        wasAttackDown = attackDown;
        if (!justPressed || mc.screen != null) {
            return;
        }
        if (mc.hitResult == null || mc.hitResult.getType() != Type.MISS) {
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof ItemSlashBlade)) {
            return;
        }
        String key = CapabilitySlashBlade.BLADESTATE.maybeGet(held)
                .map(ISlashBladeState::getTranslationKey).orElse("");
        if ("item.shizuku.mosmicblade".equals(key)) {
            mc.player.swing(InteractionHand.MAIN_HAND);
            PacketHandler.sendToServer(new PhantomSlashPacket());
        }
    }
}
