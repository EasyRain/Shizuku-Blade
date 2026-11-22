package com.buwan.shizuku.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * 1.21 起 {@code ItemStack} 改用数据组件，{@code getTag()/getOrCreateTag()/hasTag()} 都已移除。
 *
 * <p>这里用原版的 {@link DataComponents#CUSTOM_DATA}（对应旧版物品 NBT）做一层薄封装，
 * 让审判逻辑里原来的「读写物品 NBT」写法可以基本保持原样。</p>
 */
public final class ItemNbt {
    private ItemNbt() {}

    /** 只读取，不创建。没有自定义数据时返回 null。 */
    @Nullable
    public static CompoundTag get(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    public static boolean has(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty();
    }

    /** 写入（会覆盖原有自定义数据）。 */
    public static void set(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 读取后修改再写回；没有数据时以空 tag 开始。 */
    public static CompoundTag edit(ItemStack stack, java.util.function.Consumer<CompoundTag> mutator) {
        CompoundTag tag = get(stack);
        if (tag == null) tag = new CompoundTag();
        mutator.accept(tag);
        set(stack, tag);
        return tag;
    }
}
