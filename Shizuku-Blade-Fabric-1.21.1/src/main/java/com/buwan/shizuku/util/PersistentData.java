package com.buwan.shizuku.util;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Forge 的 {@code getPersistentData()} 在 Fabric 上的替代实现。
 *
 * <p>用按身份弱引用的侧表给实体/方块实体挂一份附加 NBT，物品栈直接用自身 tag；
 * 对象被回收时自动清理。</p>
 *
 * <p><b>注意</b>：侧表**不写存档**（只在当前会话有效）。审判遭遇 id 这类数据本来就是一局一换，
 * 所以这样处理足够；若将来需要跨存档保留，应改成在各自的 {@code addAdditionalSaveData} 里单独存
 * （试过用 mixin 注入 Entity#addAdditionalSaveData，1.20.1 的 intermediary 方法体太特殊，
 * 注入点定位不稳定，会拖垮启动，已放弃）。</p>
 */
public final class PersistentData {
    private static final Map<Object, CompoundTag> SIDE = Collections.synchronizedMap(new WeakHashMap<>());

    private PersistentData() {}

    public static CompoundTag of(Entity entity) {
        return SIDE.computeIfAbsent(entity, ignored -> new CompoundTag());
    }

    public static CompoundTag of(BlockEntity blockEntity) {
        return SIDE.computeIfAbsent(blockEntity, ignored -> new CompoundTag());
    }

    public static CompoundTag of(ItemStack stack) {
        return stack.getOrCreateTag();
    }

    public static CompoundTag of(Object holder) {
        if (holder instanceof Entity entity) return of(entity);
        if (holder instanceof BlockEntity blockEntity) return of(blockEntity);
        if (holder instanceof ItemStack stack) return of(stack);
        return SIDE.computeIfAbsent(holder, ignored -> new CompoundTag());
    }
}
