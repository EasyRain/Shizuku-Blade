package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.JudgmentVisuals;
import com.buwan.shizuku.client.TimeStopShaderManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 雾色沉浸 —— 1.20.1/NeoForge 的 {@code ViewportEvent.ComputeFogColor} 的 Fabric 等价物。
 *
 * <p>Fabric 没有雾色事件，但原版每帧在 {@link FogRenderer#setupColor} 里算完雾色后
 * 调 {@code RenderSystem.setShaderFogColor(...)}。这里在 {@code setupColor} 的 TAIL
 * 读取已写入的雾色（{@link RenderSystem#getShaderFogColor()}）做两次插值再写回：</p>
 * <ul>
 *   <li><b>审判域球</b>：{@code lerp(immersion * 0.94, 原色, (0.20, 0.012, 0.047))}
 *       —— 与 NeoForge 1.21.1 {@code JudgmentVisuals#fog} 逐项一致；</li>
 *   <li><b>时停</b>：{@code lerp(strength * 0.1, 原色, getTimeStopTint())}
 *       —— 与 NeoForge 1.21.1 {@code TimeStopScreenEffect#onFogColor} 一致。</li>
 * </ul>
 *
 * <p>只改 RGB，alpha 保持原值（雾强度通道）。</p>
 */
@Mixin(FogRenderer.class)
public abstract class FogColorMixin {

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void shizuku$fogColor(Camera camera, float partialTick, ClientLevel level,
                                         int renderDistance, float darkenWorldAmount, CallbackInfo ci) {
        float[] current = RenderSystem.getShaderFogColor();
        if (current == null || current.length < 3) {
            return;
        }
        float r = current[0];
        float g = current[1];
        float b = current[2];
        boolean changed = false;

        float amount = JudgmentVisuals.fogImmersion(partialTick);
        if (amount > 0.0F) {
            r = Mth.lerp(amount, r, 0.20F);
            g = Mth.lerp(amount, g, 0.012F);
            b = Mth.lerp(amount, b, 0.047F);
            changed = true;
        }

        if (TimeStopShaderManager.isActive()) {
            float[] tint = TimeStopShaderManager.getTimeStopTint();
            float strength = TimeStopShaderManager.getCurrentStrength();
            float mix = strength * 0.1F;
            r = r * (1.0F - mix) + tint[0] * mix;
            g = g * (1.0F - mix) + tint[1] * mix;
            b = b * (1.0F - mix) + tint[2] * mix;
            changed = true;
        }

        if (changed) {
            RenderSystem.setShaderFogColor(r, g, b);
        }
    }
}
