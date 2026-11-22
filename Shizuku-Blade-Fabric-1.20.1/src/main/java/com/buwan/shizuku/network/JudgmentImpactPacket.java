package com.buwan.shizuku.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * S2C：审判落剑的位置/朝向，客户端据此播放斩击动画。
 *
 * <p>Fabric 版：客户端接收在 {@code ShizukuFabricClient#registerClientReceivers()} 里注册，
 * 直接调用 {@code JudgmentVisuals.play(this)}（不需要 Forge 的 DistExecutor）。</p>
 */
public record JudgmentImpactPacket(UUID caster, long startTick, int phaseTick, Vec3 origin, float yaw, float pitch)
        implements ShizukuPacket {
    public static final ResourceLocation ID = new ResourceLocation("shizuku", "judgment_impact");

    public static JudgmentImpactPacket from(ServerPlayer player, int phaseTick) {
        long start = player.level().getGameTime() - phaseTick;
        return new JudgmentImpactPacket(player.getUUID(), start, phaseTick, player.getEyePosition(),
                player.getYRot(), player.getXRot());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(caster);
        buf.writeLong(startTick);
        buf.writeVarInt(phaseTick);
        buf.writeDouble(origin.x);
        buf.writeDouble(origin.y);
        buf.writeDouble(origin.z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    public static JudgmentImpactPacket read(FriendlyByteBuf buf) {
        return new JudgmentImpactPacket(buf.readUUID(), buf.readLong(), buf.readVarInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat());
    }
}
