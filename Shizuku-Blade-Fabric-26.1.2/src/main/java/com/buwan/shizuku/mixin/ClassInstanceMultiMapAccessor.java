package com.buwan.shizuku.mixin;

import java.util.List;
import java.util.Map;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @Accessor：拿到 ClassInstanceMultiMap 的 byClass（按类分组的实体集合），
 * 移除实体时要把这里也一并清理，否则实体还会被再次遍历到。
 */
@Mixin(ClassInstanceMultiMap.class)
public interface ClassInstanceMultiMapAccessor {
    @Accessor("byClass")
    Map<Class<?>, List<?>> ymsa$getByClass();
}
