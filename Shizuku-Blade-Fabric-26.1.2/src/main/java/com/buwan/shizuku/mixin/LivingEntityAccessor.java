package com.buwan.shizuku.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    /** 暴露私有静态字段 DATA_HEALTH_ID，用于对顽固 Boss 直接改写同步血量。 */
    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> shizuku$getDataHealthId() {
        throw new AssertionError();
    }
}
