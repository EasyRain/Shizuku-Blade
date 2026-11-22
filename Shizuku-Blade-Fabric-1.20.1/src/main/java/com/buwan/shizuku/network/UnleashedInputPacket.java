package com.buwan.shizuku.network;

import com.buwan.shizuku.sa.UnleashedJudgment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S：解放（审判）输入与开关。对应 Forge 版的同名包。
 *
 * <p>1.20.1 Fabric 走的是按通道名收发，所以这里实现 {@link ShizukuPacket}，
 * 服务端接收在 {@code PacketHandler#registerUnleashedInput()} 里注册。</p>
 */
public record UnleashedInputPacket(Action action) implements ShizukuPacket {
    public static final ResourceLocation ID = new ResourceLocation("shizuku", "unleashed_input");

    public enum Action { PRESS, RELEASE, CANCEL, ENABLE, DISABLE, DAMAGE_PLAYERS_ON, DAMAGE_PLAYERS_OFF }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(action);
    }

    public static UnleashedInputPacket read(FriendlyByteBuf buf) {
        return new UnleashedInputPacket(buf.readEnum(Action.class));
    }

    /** 服务端处理：直接交给解放逻辑。 */
    public void handle(net.minecraft.server.level.ServerPlayer sender) {
        if (sender != null) {
            UnleashedJudgment.input(sender, action);
        }
    }
}
