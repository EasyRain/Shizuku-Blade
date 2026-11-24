package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.JudgmentVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.util.Mth;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 审判「雾沉浸」—— 站在域球内时把雾色染成暗红（1.20.1/NeoForge 1.21.1 的
 * {@code ViewportEvent.ComputeFogColor} 的 26.1 Fabric 等价物）。
 *
 * <p>Fabric 没有雾色事件；26.1 的雾已经改成「每帧算一次 {@link FogData} 再写进 Fog UBO」，
 * 所以在 {@code FogRenderer#setupFog} 返回处改 {@link FogData#color} 即可，
 * 与原版事件的插值公式逐项一致：{@code lerp(immersion * 0.94, 原色, (0.20, 0.012, 0.047))}。</p>
 *
 * <p>⚠️ 只改 RGB，不动 alpha（w 是雾的强度通道，改了会影响可见度）。</p>
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererJudgmentMixin {

    @Inject(
            method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;"
                    + "FLnet/minecraft/client/multiplayer/ClientLevel;)"
                    + "Lnet/minecraft/client/renderer/fog/FogData;",
            at = @At("RETURN"))
    private void shizuku$judgmentFog(Camera camera, int renderDistance, DeltaTracker delta,
                                     float partialTick, ClientLevel level,
                                     CallbackInfoReturnable<FogData> cir) {
        FogData data = cir.getReturnValue();
        if (data == null || data.color == null) {
            return;
        }
        float amount = JudgmentVisuals.fogImmersion(partialTick);
        if (amount <= 0.0F) {
            return;
        }
        Vector4f color = data.color;
        color.x = Mth.lerp(amount, color.x, 0.20F);
        color.y = Mth.lerp(amount, color.y, 0.012F);
        color.z = Mth.lerp(amount, color.z, 0.047F);
    }
}
