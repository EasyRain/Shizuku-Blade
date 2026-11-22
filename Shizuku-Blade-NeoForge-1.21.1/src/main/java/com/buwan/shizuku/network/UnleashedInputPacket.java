package com.buwan.shizuku.network;

import com.buwan.shizuku.sa.UnleashedJudgment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：解放（审判）输入与开关。
 *
 * <p>服务端会在 {@link UnleashedJudgment#input} 里做 OP 校验（权限等级 ≥2），
 * 普通玩家即便改客户端配置也过不了这一关。</p>
 */
public record UnleashedInputPacket(Action action) implements CustomPacketPayload {
    public static final Type<UnleashedInputPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shizuku", "unleashed_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnleashedInputPacket> STREAM_CODEC =
            CustomPacketPayload.codec(UnleashedInputPacket::write, UnleashedInputPacket::new);

    public enum Action { PRESS, RELEASE, CANCEL, ENABLE, DISABLE, DAMAGE_PLAYERS_ON, DAMAGE_PLAYERS_OFF }

    private UnleashedInputPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readEnum(Action.class));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(action);
    }

    @Override
    public Type<UnleashedInputPacket> type() {
        return TYPE;
    }

    public static void handle(UnleashedInputPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                UnleashedJudgment.input(player, packet.action());
            }
        });
    }
}
