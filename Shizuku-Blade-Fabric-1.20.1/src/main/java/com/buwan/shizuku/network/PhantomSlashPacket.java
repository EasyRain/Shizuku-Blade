package com.buwan.shizuku.network;

import com.buwan.shizuku.sa.StarRiverSlash;
import java.util.function.Supplier;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * 客户端 -> 服务端：触发「星河流转（幻影斩）」。
 */
public class PhantomSlashPacket {
    public void encode(FriendlyByteBuf buf) {
    }

    public static PhantomSlashPacket decode(FriendlyByteBuf buf) {
        return new PhantomSlashPacket();
    }

    public void handle(Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                String key = player.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).map(ISlashBladeState::getTranslationKey).orElse("");
                if ("item.shizuku.mosmicblade".equals(key)) {
                    StarRiverSlash.firePhantomSlash(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
