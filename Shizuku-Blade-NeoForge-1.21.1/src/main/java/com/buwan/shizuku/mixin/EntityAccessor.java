package com.buwan.shizuku.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @Accessor：直接读写 Entity.removalReason 与 isAddedToWorld 两个私有标记，
 * 绕过原版对「已移除实体」的保护逻辑。
 */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("levelCallback")
    net.minecraft.world.level.entity.EntityInLevelCallback ymsa$getLevelCallback();

    @Accessor("removalReason")
    void ymsa$setRemovalReason(RemovalReason var1);

    @Accessor("isAddedToWorld")
    void ymsa$setAddedToWorld(boolean var1);
}
