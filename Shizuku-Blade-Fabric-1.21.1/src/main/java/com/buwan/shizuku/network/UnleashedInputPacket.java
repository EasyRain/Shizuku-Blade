package com.buwan.shizuku.network;

import com.buwan.shizuku.sa.UnleashedJudgment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** C2S：解放（审判）输入与开关（1.21.1 Fabric payload 版）。服务端仍会校验 OP。 */
public record UnleashedInputPacket(Action action) implements CustomPacketPayload {
    public static final Type<UnleashedInputPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shizuku", "unleashed_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnleashedInputPacket> STREAM_CODEC =
            StreamCodec.ofMember(UnleashedInputPacket::write, UnleashedInputPacket::new);

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

    public void handleServer(ServerPlayer player) {
        UnleashedJudgment.input(player, action);
    }
}
