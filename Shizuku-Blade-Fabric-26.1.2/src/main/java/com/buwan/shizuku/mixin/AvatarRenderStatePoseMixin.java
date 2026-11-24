package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.JudgmentPoseFlag;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 给 {@link AvatarRenderState} 挂上审判姿势参数（见 {@link JudgmentPoseFlag} 的说明）。 */
@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStatePoseMixin implements JudgmentPoseFlag {

    @Unique
    private float shizuku$judgmentTime = -1.0F;

    @Unique
    private boolean shizuku$judgmentRightArm = true;

    @Override
    public void shizuku$setJudgmentPose(float time, boolean rightArm) {
        this.shizuku$judgmentTime = time;
        this.shizuku$judgmentRightArm = rightArm;
    }

    @Override
    public boolean shizuku$hasJudgmentPose() {
        return this.shizuku$judgmentTime >= 0.0F;
    }

    @Override
    public float shizuku$judgmentPoseTime() {
        return this.shizuku$judgmentTime;
    }

    @Override
    public boolean shizuku$judgmentPoseRightArm() {
        return this.shizuku$judgmentRightArm;
    }
}
