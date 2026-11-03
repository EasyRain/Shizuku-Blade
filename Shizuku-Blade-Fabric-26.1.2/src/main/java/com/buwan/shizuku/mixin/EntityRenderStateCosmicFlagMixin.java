package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.CosmicFxFlag;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 给所有实体 render state 加一个「本次绘制是否为宇宙刀特效」的布尔标记。
 *
 * <p>选中 {@code EntityRenderState}（共同父类）而不是分别加在 SlashEffect/Drive 两个
 * render state 上：一个 mixin 覆盖两条路径，且每个 render state 实例各有一份，
 * 不会像「存在渲染器实例字段上」那样被同一类型的下一个实体覆盖。</p>
 */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateCosmicFlagMixin implements CosmicFxFlag {

    @Unique
    private boolean shizuku$cosmicFx;

    @Unique
    private float shizuku$baseSize = 1.0F;

    @Override
    public boolean shizuku$isCosmicFx() {
        return this.shizuku$cosmicFx;
    }

    @Override
    public void shizuku$setCosmicFx(boolean cosmic) {
        this.shizuku$cosmicFx = cosmic;
    }

    @Override
    public float shizuku$getBaseSize() {
        return this.shizuku$baseSize;
    }

    @Override
    public void shizuku$setBaseSize(float baseSize) {
        this.shizuku$baseSize = baseSize;
    }
}
