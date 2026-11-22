package com.buwan.shizuku.util;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Forge 的 {@code getPersistentData()} 在 Fabric 上没有对应物，这里做一层等价封装。
 *
 * <p>方块实体与实体用按身份弱引用的侧表（对象被回收时自动消失），物品栈直接用自身的 tag；
 * 这样审判遭遇 id 之类的数据在同一个世界会话里能保持一致。</p>
 *
 * <p>已知差异：侧表**不写进存档**，重启后实体/方块的附加数据会丢失；
 * 需要跨存档保留的字段应在自己的 NBT 读写里单独存（审判逻辑本身只用会话期数据）。</p>
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
