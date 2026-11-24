package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.JudgmentPoseFlag;
import com.buwan.shizuku.sa.Ymsa;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 审判持刀姿势（26.1 版）。
 *
 * <p>数值与 1.20.1 原版逐项一致（brace → slash → lift → insert → click 五段缓动 + 关节覆盖），
 * 区别只在于「时间与主手」来自渲染状态（{@link JudgmentPoseFlag}，由
 * {@code AvatarRendererPoseMixin} 在 extract 段写入），而不是渲染时去摸实体 ——
 * 26.1 的 {@code PlayerModel#setupAnim} 只接收渲染状态快照。</p>
 */
@Mixin(PlayerModel.class)
public abstract class JudgmentPlayerPoseMixin {

    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("TAIL"), require = 0)
    private void shizuku$poseJudgment(AvatarRenderState state, CallbackInfo ci) {
        if (!(state instanceof JudgmentPoseFlag flag) || !flag.shizuku$hasJudgmentPose()) {
            return;
        }
        float time = flag.shizuku$judgmentPoseTime();
        if (time < 0.0F || time >= Ymsa.END_TICK) {
            return;
        }
        PlayerModel model = (PlayerModel) (Object) this;
        boolean right = flag.shizuku$judgmentPoseRightArm();
        float side = right ? 1 : -1;
        float brace = shizuku$ease(time / 5);
        float slash = shizuku$ease((time - 6) / (Ymsa.STRIKE_TICK + 1 - 6));
        float lift = shizuku$ease((time - 18) / 13);
        float insert = shizuku$ease((time - 32) / 20);
        float click = shizuku$ease((time - 52) / (Ymsa.SHEATH_TICK - 52));
        float weight = brace * (1 - shizuku$ease((time - Ymsa.SHEATH_TICK) / (Ymsa.END_TICK - Ymsa.SHEATH_TICK)));
        float chest = Mth.lerp(lift, Mth.lerp(slash, -0.58F, 0.76F), -0.25F) * (1 - 0.7F * insert);
        model.body.yRot += side * chest * weight;
        model.body.xRot += (0.17F - 0.12F * lift) * weight;
        model.head.yRot -= side * chest * 0.55F * weight;
        model.head.xRot += (0.10F + 0.12F * insert) * weight;
        ModelPart sword = right ? model.rightArm : model.leftArm;
        ModelPart offhand = right ? model.leftArm : model.rightArm;
        float swordYaw = Mth.lerp(insert, Mth.lerp(lift, Mth.lerp(slash, -0.90F, 1.02F), 0.22F), -0.80F);
        float swordPitch = Mth.lerp(insert, Mth.lerp(lift, Mth.lerp(slash, -0.62F, -1.60F), -1.32F), -0.72F);
        float swordRoll = Mth.lerp(lift, Mth.lerp(slash, -0.47F, 0.82F), -0.36F);
        sword.yRot = Mth.lerp(weight, sword.yRot, side * (swordYaw - 0.12F * click));
        sword.xRot = Mth.lerp(weight, sword.xRot, swordPitch + 0.12F * click);
        sword.zRot = Mth.lerp(weight, sword.zRot, side * swordRoll);
        offhand.xRot = Mth.lerp(weight, offhand.xRot, -0.69F - 0.27F * lift + 0.13F * insert);
        offhand.yRot = Mth.lerp(weight, offhand.yRot, side * (0.55F + 0.17F * insert));
        offhand.zRot = Mth.lerp(weight, offhand.zRot, side * 0.18F);
        // 26.1 移除了 ModelPart#copyFrom，改为逐项复制姿态（外套/袖子/帽子跟随本体）
        shizuku$copyPose(model.body, model.jacket);
        shizuku$copyPose(model.leftArm, model.leftSleeve);
        shizuku$copyPose(model.rightArm, model.rightSleeve);
        shizuku$copyPose(model.head, model.hat);
    }

    /** 26.1：{@code ModelPart#copyFrom} 已删除，这里复制位置/旋转/缩放/可见性。 */
    @Unique
    private static void shizuku$copyPose(ModelPart from, ModelPart to) {
        to.setPos(from.x, from.y, from.z);
        to.setRotation(from.xRot, from.yRot, from.zRot);
        to.xScale = from.xScale;
        to.yScale = from.yScale;
        to.zScale = from.zScale;
        to.visible = from.visible;
    }

    @Unique
    private static float shizuku$ease(float time) {
        float t = Mth.clamp(time, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
