package com.buwan.shizuku.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @Accessor：拿到 EntityTickList 的 active / passive 两张 tick 表，
 * 把实体从 tick 列表里摘掉（否则它还会继续 tick）。
 */
@Mixin(EntityTickList.class)
public interface EntityTickListAccessor {
    @Accessor("active")
    Int2ObjectMap<Entity> ymsa$getActive();

    @Accessor("passive")
    Int2ObjectMap<Entity> ymsa$getPassive();
}
