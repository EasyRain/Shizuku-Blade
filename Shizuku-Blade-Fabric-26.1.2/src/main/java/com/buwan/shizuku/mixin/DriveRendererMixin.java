package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.CosmicFx;
import com.buwan.shizuku.client.CosmicFxFlag;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.entity.DriveRenderer;
import mods.flammpfeil.slashblade.client.renderer.entity.state.DriveRenderState;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 宇宙刀的刀光星光（26.1，对应 1.20.1 原版 {@code DriveRendererMixin}）。
 *
 * <p>原版在 {@code render(...)} 的 HEAD <b>取消</b>原发光层，改画自带的
 * {@code shizuku:model/util/daoguang.obj}（12 面）套 cosmic 管线 —— 所以这里也用
 * HEAD + cancel（与斩击的「追加一层」不同，原版就是整层替换）。</p>
 *
 * <p>判定照旧在 extract 段做（submit 段拿不到 entity），见 {@link CosmicFxFlag}。</p>
 */
@Mixin(value = DriveRenderer.class, remap = false)
public abstract class DriveRendererMixin {

    @Inject(
            method = "extractRenderState(Lmods/flammpfeil/slashblade/entity/EntityDrive;"
                    + "Lmods/flammpfeil/slashblade/client/renderer/entity/state/DriveRenderState;F)V",
            at = @At("TAIL"))
    private void shizuku$markCosmicDrive(EntityDrive entity, DriveRenderState state,
                                         float partialTick, CallbackInfo ci) {
        if (state instanceof CosmicFxFlag flag) {
            flag.shizuku$setCosmicFx(CosmicFx.isCosmicShooter(entity.getShooter()));
            // 26.1 的 DriveRenderState 不带 baseSize（上游 26.1 刀光改固定缩放），
            // 这里自己带过去，供 CosmicFx.submitDrive 还原原版 0.02*max(1,baseSize)
            flag.shizuku$setBaseSize(entity.getBaseSize());
        }
    }

    @Inject(
            method = "submit(Lmods/flammpfeil/slashblade/client/renderer/entity/state/DriveRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void shizuku$renderDaoguang(DriveRenderState state, PoseStack poseStack,
                                        SubmitNodeCollector collector, CameraRenderState camera,
                                        CallbackInfo ci) {
        if (state instanceof CosmicFxFlag flag && flag.shizuku$isCosmicFx()) {
            // 原版：宇宙刀光整层替换（先取消，再自己画；淡出时也是什么都不剩）
            ci.cancel();
            CosmicFx.submitDrive(state, poseStack, collector);
        }
    }
}
