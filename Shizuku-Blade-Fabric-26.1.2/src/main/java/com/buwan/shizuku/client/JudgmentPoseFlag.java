package com.buwan.shizuku.client;

/**
 * 审判持刀姿势在「渲染状态」上的载体（26.1）。
 *
 * <p>1.20.1/1.21.1 的 `JudgmentPlayerPoseMixin` 直接注入
 * {@code PlayerModel#setupAnim(LivingEntity, …)}，那里能拿到实体、读到刀的连招状态。
 * 26.1 把该方法改成只接收 {@code AvatarRenderState}（快照），实体已经不可达 ⇒ 姿势动画无从计算。</p>
 *
 * <p>解法（与 {@code CosmicFxFlag} 同款）：在 {@code AvatarRenderer#extractRenderState}
 * （**实体还在**）时把「审判进行到第几 tick、主手是哪只手」写进渲染状态的 {@code @Unique} 字段，
 * 再由 {@code PlayerModel#setupAnim(AvatarRenderState)} 读出来复现同一套缓动与关节覆盖。</p>
 */
public interface JudgmentPoseFlag {

    /** 写入姿势参数（{@code time < 0} 表示本次不播放）。 */
    void shizuku$setJudgmentPose(float time, boolean rightArm);

    boolean shizuku$hasJudgmentPose();

    float shizuku$judgmentPoseTime();

    boolean shizuku$judgmentPoseRightArm();
}
