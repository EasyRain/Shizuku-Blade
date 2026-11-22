package com.buwan.shizuku.network;

import com.buwan.shizuku.sa.UnleashedJudgment;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;

public record UnleashedInputPacket(Action action) {
    public enum Action { PRESS, RELEASE, CANCEL, ENABLE, DISABLE, DAMAGE_PLAYERS_ON, DAMAGE_PLAYERS_OFF }

    public void encode(FriendlyByteBuf buf) { buf.writeEnum(action); }
    public static UnleashedInputPacket decode(FriendlyByteBuf buf) {
        return new UnleashedInputPacket(buf.readEnum(Action.class));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        var sender = context.get().getSender();
        if (sender != null) UnleashedJudgment.input(sender, action);
        context.get().setPacketHandled(true);
    }
}
