package com.buwan.shizuku.util;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Forge 的 {@code getPersistentData()} 在 Fabric 上的等价物。
 *
 * <p>实体与方块实体由 mixin 注入一份附加 NBT（{@link PersistentDataHolder}），**会随存档读写**；
 * 物品栈直接用自身的 tag；其余对象退回按身份弱引用的侧表（会话期有效）。</p>
 */
public final class PersistentData {
    private static final Map<Object, CompoundTag> SIDE = Collections.synchronizedMap(new WeakHashMap<>());

    private PersistentData() {}

    public static CompoundTag of(Entity entity) {
        if (entity instanceof PersistentDataHolder holder) {
            return holder.shizuku$persistentData();
        }
        return SIDE.computeIfAbsent(entity, ignored -> new CompoundTag());
    }

    public static CompoundTag of(BlockEntity blockEntity) {
        if (blockEntity instanceof PersistentDataHolder holder) {
            return holder.shizuku$persistentData();
        }
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
