package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.CosmicFx;
import com.buwan.shizuku.client.CosmicFxFlag;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.entity.SlashEffectRenderer;
import mods.flammpfeil.slashblade.client.renderer.entity.state.SlashEffectRenderState;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 宇宙刀的斩击星光（26.1，对应 1.20.1 原版 {@code SlashEffectRendererMixin}）。
 *
 * <p>原版注入 {@code render(...)} 的 TAIL、<b>不取消</b>原效果，直接重放姿态链再画一层
 * cosmic。26.1 拆成 extract + submit 两段，所以拆成两个注入：</p>
 * <ul>
 *   <li>{@code extractRenderState}（这里还能拿到 entity）→ 把「发射者手持宇宙刀」写进
 *       render state 的标记（{@link CosmicFxFlag}）；</li>
 *   <li>{@code submit} 的 TAIL（相当于旧的 render TAIL）→ 重放姿态链 + 提交星空层。</li>
 * </ul>
 *
 * <p>⚠️ 方法描述符必须写全：类里还有 MC 的<b>桥接方法</b>
 * {@code submit(EntityRenderState, ...)} / {@code extractRenderState(Entity, ...)}，
 * 只写方法名会撞上桥接方法。26.1.2 运行时就是 Mojmap 名（已发布的 jar 里就是这套名字），
 * 所以 {@code remap = false} + Mojmap 描述符是对的。</p>
 */
@Mixin(value = SlashEffectRenderer.class, remap = false)
public abstract class SlashEffectRendererMixin {

    @Inject(
            method = "extractRenderState(Lmods/flammpfeil/slashblade/entity/EntitySlashEffect;"
                    + "Lmods/flammpfeil/slashblade/client/renderer/entity/state/SlashEffectRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void shizuku$markCosmicSlash(EntitySlashEffect entity, SlashEffectRenderState state,
                                         float partialTick, CallbackInfo ci) {
        if (state instanceof CosmicFxFlag flag) {
            flag.shizuku$setCosmicFx(CosmicFx.isCosmicShooter(entity.getShooter()));
        }
    }

    @Inject(
            method = "submit(Lmods/flammpfeil/slashblade/client/renderer/entity/state/SlashEffectRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("TAIL"), require = 0)
    private void shizuku$renderCosmicSlash(SlashEffectRenderState state, PoseStack poseStack,
                                           SubmitNodeCollector collector, CameraRenderState camera,
                                           CallbackInfo ci) {
        CosmicFx.submitSlashEffect(state, poseStack, collector);
    }
}
