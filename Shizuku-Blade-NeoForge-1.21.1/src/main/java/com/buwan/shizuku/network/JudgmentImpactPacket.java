package com.buwan.shizuku.network;

import com.buwan.shizuku.client.JudgmentVisuals;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.DistExecutor;
import net.neoforged.neoforge.network.NetworkEvent.Context;

public record JudgmentImpactPacket(UUID caster, long startTick, int phaseTick, Vec3 origin, float yaw, float pitch) {
    public static JudgmentImpactPacket from(ServerPlayer player, int phaseTick) {
        return from(player, phaseTick, player.getYRot(), player.getXRot());
    }

    /**
     * 用指定朝向构造包。
     *
     * <p>审判落剑有延迟：若在施法到落剑之间转头，客户端会按「落剑那一刻」的朝向算落点，
     * 而击杀范围是以玩家为中心、施法时就定了——两者就对不上。所以服务端在
     * {@code beginJudgment} 时锁定朝向，后续阶段都用这份朝向发包。</p>
     */
    public static JudgmentImpactPacket from(ServerPlayer player, int phaseTick, float yaw, float pitch) {
        long start = player.level().getGameTime() - phaseTick;
        return new JudgmentImpactPacket(player.getUUID(), start, phaseTick, player.getEyePosition(), player.getYRot(), player.getXRot());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(caster);
        buf.writeLong(startTick);
        buf.writeVarInt(phaseTick);
        buf.writeDouble(origin.x);
        buf.writeDouble(origin.y);
        buf.writeDouble(origin.z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    public static JudgmentImpactPacket decode(FriendlyByteBuf buf) {
        return new JudgmentImpactPacket(buf.readUUID(), buf.readLong(), buf.readVarInt(),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat());
    }

    public void handle(Supplier<Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> JudgmentVisuals.play(this));
        ctx.get().setPacketHandled(true);
    }
}
