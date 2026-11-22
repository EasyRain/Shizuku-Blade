package com.buwan.shizuku.network;

import com.buwan.shizuku.client.JudgmentVisuals;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C：审判落剑的位置/朝向，客户端据此播放斩击动画。
 *
 * <p>1.21.1 的网络层是 payload API（{@link CustomPacketPayload} + {@code RegisterPayloadHandlersEvent}），
 * 不再有 Forge 的 SimpleChannel。</p>
 */
public record JudgmentImpactPacket(UUID caster, long startTick, int phaseTick, Vec3 origin, float yaw, float pitch)
        implements CustomPacketPayload {

    public static final Type<JudgmentImpactPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shizuku", "judgment_impact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JudgmentImpactPacket> STREAM_CODEC =
            CustomPacketPayload.codec(JudgmentImpactPacket::write, JudgmentImpactPacket::new);

    public static JudgmentImpactPacket from(ServerPlayer player, int phaseTick) {
        return from(player, phaseTick, player.getYRot(), player.getXRot());
    }

    /**
     * 用指定朝向构造包（审判期间锁定施法朝向，避免落剑跟着鼠标跑）。
     */
    public static JudgmentImpactPacket from(ServerPlayer player, int phaseTick, float yaw, float pitch) {
        long start = player.level().getGameTime() - phaseTick;
        return new JudgmentImpactPacket(player.getUUID(), start, phaseTick, player.getEyePosition(), yaw, pitch);
    }

    private JudgmentImpactPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readLong(), buf.readVarInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(), buf.readFloat());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(caster);
        buf.writeLong(startTick);
        buf.writeVarInt(phaseTick);
        buf.writeDouble(origin.x);
        buf.writeDouble(origin.y);
        buf.writeDouble(origin.z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    @Override
    public Type<JudgmentImpactPacket> type() {
        return TYPE;
    }

    public static void handle(JudgmentImpactPacket packet, IPayloadContext ctx) {
        if (FMLEnvironment.dist.isClient()) {
            ctx.enqueueWork(() -> JudgmentVisuals.play(packet));
        }
    }
}
