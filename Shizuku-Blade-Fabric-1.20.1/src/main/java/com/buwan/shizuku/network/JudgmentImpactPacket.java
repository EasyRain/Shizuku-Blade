package com.buwan.shizuku.network;

import com.buwan.shizuku.client.JudgmentVisuals;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public record JudgmentImpactPacket(UUID caster, long startTick, int phaseTick, Vec3 origin, float yaw, float pitch) {
    public static JudgmentImpactPacket from(ServerPlayer player, int phaseTick) {
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
