package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.JudgmentVisuals;
import com.buwan.shizuku.network.PacketHandler;
import com.buwan.shizuku.network.UnleashedInputPacket;
import com.buwan.shizuku.network.UnleashedInputPacket.Action;
import com.buwan.shizuku.sa.UnleashedJudgment;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class UnleashedInputMixin {
    @Unique private boolean shizuku$held;
    @Unique private boolean shizuku$enabled;
    @Unique private boolean shizuku$damagePlayers;
    @Unique private ClientPacketListener shizuku$connection;

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void shizuku$sampleUse(CallbackInfo ci) {
        Minecraft mc = (Minecraft)(Object)this;
        if (shizuku$connection != mc.getConnection()) {
            shizuku$held = false;
            shizuku$enabled = false;
            shizuku$damagePlayers = false;
            shizuku$connection = mc.getConnection();
        }
        if (shizuku$connection == null || mc.player == null) return;
        boolean damagePlayers = JudgmentVisuals.DAMAGE_PLAYERS.get();
        if (damagePlayers != shizuku$damagePlayers) {
            PacketHandler.INSTANCE.sendToServer(new UnleashedInputPacket(damagePlayers ? Action.DAMAGE_PLAYERS_ON : Action.DAMAGE_PLAYERS_OFF));
            shizuku$damagePlayers = damagePlayers;
        }
        boolean enabled = JudgmentVisuals.UNLEASHED.get();
        if (enabled != shizuku$enabled) {
            PacketHandler.INSTANCE.sendToServer(new UnleashedInputPacket(enabled ? Action.ENABLE : Action.DISABLE));
            shizuku$enabled = enabled;
        }
        boolean eligible = enabled && mc.screen == null && mc.isWindowActive() && !mc.isPaused()
            && UnleashedJudgment.isBlade(mc.player.getMainHandItem());
        var key = mc.options.keyUse.getKey();
        long window = mc.getWindow().getWindow();
        boolean down = eligible && (key.getType() == InputConstants.Type.MOUSE
            ? GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS
            : key.getType() == InputConstants.Type.KEYSYM && key.getValue() >= 0
                ? InputConstants.isKeyDown(window, key.getValue()) : mc.options.keyUse.isDown());
        if (down != shizuku$held) {
            PacketHandler.INSTANCE.sendToServer(new UnleashedInputPacket(down ? Action.PRESS : eligible ? Action.RELEASE : Action.CANCEL));
            shizuku$held = down;
        }
    }
}
