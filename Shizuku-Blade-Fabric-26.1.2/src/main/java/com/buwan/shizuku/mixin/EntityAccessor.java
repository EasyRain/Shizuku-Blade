package com.buwan.shizuku.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @Accessor：直接读写 Entity.removalReason 私有标记（isAddedToWorld 是 Forge 独有字段，Fabric 没有），
 * 绕过原版对「已移除实体」的保护逻辑。
 */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("levelCallback")
    net.minecraft.world.level.entity.EntityInLevelCallback ymsa$getLevelCallback();

    @Accessor("removalReason")
    void ymsa$setRemovalReason(RemovalReason var1);
}
