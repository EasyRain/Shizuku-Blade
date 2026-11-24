package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.JudgmentVisuals;
import com.buwan.shizuku.client.TimeStopShaderManager;
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
 * <p>同一个注入点还承担**时停**的雾色染（1.20.1/NeoForge 的
 * {@code TimeStopScreenEffect#onFogColor}）：强度 {@code strength × 0.1}，颜色取
 * {@link TimeStopShaderManager#getTimeStopTint()}（满强度 = 暖黄 1.0/0.84/0.0）。</p>
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
        // ① 审判域球：朝暗红插值（NeoForge 1.21.1 JudgmentVisuals#fog 逐项一致）
        color.x = Mth.lerp(amount, color.x, 0.20F);
        color.y = Mth.lerp(amount, color.y, 0.012F);
        color.z = Mth.lerp(amount, color.z, 0.047F);

        // ② 时停：弱染（strength * 0.1），与 NeoForge 1.21.1 TimeStopScreenEffect#onFogColor 一致
        if (TimeStopShaderManager.isActive()) {
            float[] tint = TimeStopShaderManager.getTimeStopTint();
            float strength = TimeStopShaderManager.getCurrentStrength();
            float mix = strength * 0.1F;
            color.x = color.x * (1.0F - mix) + tint[0] * mix;
            color.y = color.y * (1.0F - mix) + tint[1] * mix;
            color.z = color.z * (1.0F - mix) + tint[2] * mix;
        }
    }
}
