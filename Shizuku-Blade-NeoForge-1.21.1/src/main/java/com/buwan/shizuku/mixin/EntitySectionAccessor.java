package com.buwan.shizuku.mixin;

import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @Accessor：拿到 EntitySection.storage（分段内的实体集合），强制移除时清理。
 */
@Mixin(EntitySection.class)
public interface EntitySectionAccessor {
    @Accessor("storage")
    ClassInstanceMultiMap<?> ymsa$getStorage();
}
