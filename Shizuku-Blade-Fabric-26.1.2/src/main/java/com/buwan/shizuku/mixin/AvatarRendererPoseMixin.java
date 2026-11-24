package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.JudgmentPoseFlag;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 采集审判姿势参数（26.1）。
 *
 * <p>1.21.1 及更早的 {@code JudgmentPlayerPoseMixin} 直接注入
 * {@code PlayerModel#setupAnim(LivingEntity, …)}；26.1 的该方法只接收渲染状态快照，
 * 实体不可达。这里在 {@code AvatarRenderer#extractRenderState}（实体仍在）里，
 * 判断「主手刀的连招 = YMSA 且审判未结束」，把已过 tick 与主手写进渲染状态。</p>
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererPoseMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;"
                    + "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void shizuku$collectJudgmentPose(Avatar entity, AvatarRenderState state, float partialTick,
                                             CallbackInfo ci) {
        if (!(state instanceof JudgmentPoseFlag flag)) {
            return;
        }
        float time = -1.0F;
        if (entity != null) {
            // 用「施法起始 gameTime」广播驱动的计时（见 JudgmentPoseClient）：
            // 库的 comboSeq/lastActionTime 会被 2.0.5 的运行时同步器持续重写，
            // 收刀段会因连招状态被换掉而让姿势瞬间消失（实测「收刀一下就回来」）。
            float elapsed = com.buwan.shizuku.client.JudgmentPoseClient.progress(entity);
            if (elapsed >= 0.0F) {
                time = elapsed + Mth.frac(state.ageInTicks);
            }
        }
        flag.shizuku$setJudgmentPose(time, entity != null && entity.getMainArm() == HumanoidArm.RIGHT);
    }
}
