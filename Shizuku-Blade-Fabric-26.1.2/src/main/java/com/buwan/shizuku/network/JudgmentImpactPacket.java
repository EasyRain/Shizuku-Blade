package com.buwan.shizuku.network;

import com.buwan.shizuku.client.JudgmentVisuals;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * S2C：审判落剑的位置/朝向，客户端据此播放斩击动画（1.21.1 Fabric payload 版）。
 */
public record JudgmentImpactPacket(UUID caster, long startTick, int phaseTick, Vec3 origin, float yaw, float pitch)
        implements CustomPacketPayload {

    public static final Type<JudgmentImpactPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("shizuku", "judgment_impact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JudgmentImpactPacket> STREAM_CODEC =
            StreamCodec.ofMember(JudgmentImpactPacket::write, JudgmentImpactPacket::new);

    public static JudgmentImpactPacket from(ServerPlayer player, int phaseTick) {
        return from(player, phaseTick, player.getYRot(), player.getXRot());
    }

    public static JudgmentImpactPacket from(ServerPlayer player, int phaseTick, float yaw, float pitch) {
        return from(player, phaseTick, yaw, pitch,
                player.getEyePosition().x, player.getEyePosition().y, player.getEyePosition().z);
    }

    /** 同时锁定施法瞬间的眼睛位置：落点不会因为施法过程中走动而漂移。 */
    public static JudgmentImpactPacket from(ServerPlayer player, int phaseTick, float yaw, float pitch,
                                            double x, double y, double z) {
        long start = player.level().getGameTime() - phaseTick;
        return new JudgmentImpactPacket(player.getUUID(), start, phaseTick, new Vec3(x, y, z), yaw, pitch);
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

    /** 客户端接收（在 ShizukuFabricClient 里注册）。 */
    public void handleClient() {
        JudgmentVisuals.play(this);
    }
}
