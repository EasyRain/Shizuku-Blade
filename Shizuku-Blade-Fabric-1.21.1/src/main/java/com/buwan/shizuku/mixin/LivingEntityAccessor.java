package com.buwan.shizuku.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    /** 死亡标记：die() 成功会置 true，用于区分「真死了」和「血量 0 但被模组吞掉」。 */
    @Accessor("dead")
    boolean shizuku$isDead();

    @Accessor("dead")
    void shizuku$setDead(boolean value);

    /** 击杀分数（awardKillScore 用）。 */
    @Accessor("deathScore")
    int shizuku$getDeathScore();

    /** protected 的掉落结算，用于「正常死亡」兜底（1.21 起多一个 ServerLevel 参数）。 */
    @Invoker("dropAllDeathLoot")
    void shizuku$dropAllDeathLoot(ServerLevel level, DamageSource source);

    /** 暴露私有静态字段 DATA_HEALTH_ID，用于对顽固 Boss 直接改写同步血量。 */
    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> shizuku$getDataHealthId() {
        throw new AssertionError();
    }
}
