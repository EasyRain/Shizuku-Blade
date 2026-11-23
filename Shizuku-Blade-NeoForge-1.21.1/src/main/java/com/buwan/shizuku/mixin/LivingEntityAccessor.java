package com.buwan.shizuku.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    /** 暴露私有静态字段 DATA_HEALTH_ID，用于对顽固 Boss 直接改写同步血量。 */
    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> shizuku$getDataHealthId() {
        throw new AssertionError();
    }

    /** 死亡标记（原版 die() 会把它置 true）。 */
    @Accessor("dead")
    boolean shizuku$isDead();

    @Accessor("dead")
    void shizuku$setDead(boolean value);

    /** 击杀分数（原版用于 awardKillScore）。 */
    @Accessor("deathScore")
    int shizuku$getDeathScore();

    /** protected 的掉落结算，用于「正常死亡」兜底。 */
    @Invoker("dropAllDeathLoot")
    void shizuku$dropAllDeathLoot(net.minecraft.server.level.ServerLevel level,
                                 net.minecraft.world.damagesource.DamageSource source);
}
