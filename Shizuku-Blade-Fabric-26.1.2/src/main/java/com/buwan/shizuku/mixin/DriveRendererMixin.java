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
            at = @At("TAIL"), require = 0)
    private void shizuku$markCosmicDrive(EntityDrive entity, DriveRenderState state,
                                         float partialTick, CallbackInfo ci) {
        if (state instanceof CosmicFxFlag flag) {
            boolean cosmicShooter = CosmicFx.isCosmicShooter(entity.getShooter());
            boolean ownBlade = CosmicFx.isOwnBladeShooter(entity.getShooter());
            // A/B 诊断：-Dshizuku.slashfx.use_cosmic=true 时把自家刀也塞进 cosmic 那条已验证的路径，
            // 用来区分「我们的实现不对」还是「接线/门控不对」。
            boolean ab = ownBlade && CosmicFx.useCosmicPathForOwnBlades();
            flag.shizuku$setCosmicFx(cosmicShooter || ab);
            flag.shizuku$setOwnBladeFx(ownBlade && !ab);
            com.buwan.shizuku.client.slash.UpstreamSlashFx.debugExtract("drive", entity.getShooter(), flag);
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
            cancellable = true, require = 0)
    private void shizuku$renderDaoguang(DriveRenderState state, PoseStack poseStack,
                                        SubmitNodeCollector collector, CameraRenderState camera,
                                        CallbackInfo ci) {
        // 整层替换：只有我们真的画出来了才 cancel（模型缺失/淡出结束时保持上游原样，
        // 免得「接管了却什么都画不出」= 彻底不可见）。
        // 顺序：宇宙刀 → 星空（CosmicFx）；自家其它刀 → 上游画法的普通刀光（UpstreamSlashFx）。
        if (CosmicFx.submitDrive(state, poseStack, collector)) {
            ci.cancel();
            return;
        }
        // ⚠️ 止损：默认**只叠加、不 cancel**。上一版无条件 cancel 后自绘又没出画面，
        // 结果从「抽搐但可见」退化成「完全不可见」。现在只有显式
        // -Dshizuku.slashfx.replace_drive=true 且确实提交成功时才替换。
        boolean submitted = com.buwan.shizuku.client.slash.UpstreamSlashFx.submitDrive(state, poseStack, collector);
        if (submitted && com.buwan.shizuku.client.slash.UpstreamSlashFx.mayReplaceDrive()) {
            ci.cancel();
        }
    }
}
