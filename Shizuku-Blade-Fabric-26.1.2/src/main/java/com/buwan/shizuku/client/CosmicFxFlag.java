package com.buwan.shizuku.client;

/**
 * 「这次绘制要不要叠星空层」的标记载体。
 *
 * <p>26.1 的实体渲染拆成 extract（有 entity）→ submit（只有 render state）两段，
 * 而 {@code SlashEffectRenderer.submit} / {@code DriveRenderer.submit} 里拿不到发射者，
 * 判定（发射者手持宇宙刀）只能在 extract 阶段做。标记就存在 render state 上
 * （由 {@code EntityRenderStateCosmicFlagMixin} 给 {@code EntityRenderState} 加一个字段），
 * 这样即使同一渲染器连续处理多个实体也不会串味——存在渲染器实例上就会串。</p>
 *
 * <p>与 1.20.1 原版对照：原作者是在 {@code render(...)} 里直接拿 entity，26.1 只能两段接力。</p>
 */
public interface CosmicFxFlag {

    boolean shizuku$isCosmicFx();

    void shizuku$setCosmicFx(boolean cosmic);

    /**
     * 原效果的 baseSize（26.1 的 {@code DriveRenderState} <b>没有</b>这个字段 —— 上游 26.1
     * 的刀光渲染已经改成固定 {@code scale(0.015)}，不再吃 baseSize；但 1.20.1 原版的宇宙刀光
     * 用的是 {@code 0.02*max(1,baseSize)}，为了忠实还原必须自己带过来）。
     */
    float shizuku$getBaseSize();

    void shizuku$setBaseSize(float baseSize);
}
