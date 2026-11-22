package com.buwan.shizuku.network;

import com.buwan.shizuku.sa.StarRiverSlash;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端 -> 服务端：触发「星河流转（幻影斩）」。
 *
 * <p>Fabric 版走通道 API，服务端接收在 {@code PacketHandler#registerServer()} 里注册。</p>
 */
public class PhantomSlashPacket implements ShizukuPacket {
    public static final ResourceLocation ID = new ResourceLocation("shizuku", "phantom_slash");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
    }

    public static PhantomSlashPacket read(FriendlyByteBuf buf) {
        return new PhantomSlashPacket();
    }

    public void handle(ServerPlayer player) {
        if (player == null) {
            return;
        }
        String key = CapabilitySlashBlade.BLADESTATE.maybeGet(player.getMainHandItem())
                .map(ISlashBladeState::getTranslationKey).orElse("");
        if ("item.shizuku.mosmicblade".equals(key)) {
            StarRiverSlash.firePhantomSlash(player);
        }
    }
}
