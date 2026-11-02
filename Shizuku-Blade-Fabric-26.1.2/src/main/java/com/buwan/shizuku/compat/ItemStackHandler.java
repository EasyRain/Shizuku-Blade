package com.buwan.shizuku.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * NeoForge {@code ItemStackHandler} 的最小 Fabric 兼容实现。
 * 仅实现本 mod 用到的接口：容量查询 / 读写 / 抽取 / NBT 序列化。
 */
public class ItemStackHandler {
    protected NonNullList<ItemStack> stacks;

    public ItemStackHandler() {
        this(1);
    }

    public ItemStackHandler(int size) {
        this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    public int getSlots() {
        return stacks.size();
    }

    public ItemStack getStackInSlot(int slot) {
        return stacks.get(slot);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        stacks.set(slot, stack);
        onChange(slot);
    }

    public int getSlotLimit(int slot) {
        return 64;
    }

    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }

    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot < 0 || slot >= getSlots()) {
            return stack;
        }
        ItemStack existing = stacks.get(slot);
        int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(existing, stack)) {
                return stack;
            }
            limit -= existing.getCount();
        }
        if (limit <= 0) {
            return stack;
        }
        int toInsert = Math.min(limit, stack.getCount());
        if (!simulate) {
            if (existing.isEmpty()) {
                stacks.set(slot, stack.copyWithCount(toInsert));
            } else {
                existing.grow(toInsert);
            }
            onChange(slot);
        }
        return stack.copyWithCount(stack.getCount() - toInsert);
    }

    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= getSlots() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = stacks.get(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int toExtract = Math.min(amount, stack.getCount());
        ItemStack result = stack.copyWithCount(toExtract);
        if (!simulate) {
            stack.shrink(toExtract);
            if (stack.isEmpty()) {
                stacks.set(slot, ItemStack.EMPTY);
            }
            onChange(slot);
        }
        return result;
    }

    /** 子类可覆写以感知内容变化。 */
    protected void onChange(int slot) {
    }

    /** 26.1：改用 codec 存储（ValueOutput/ValueInput），不再用 ContainerHelper + CompoundTag。 */
    public void serialize(ValueOutput output) {
        // 26.1：Items 的 codec 不允许空槽（air / count 0），必须用 OPTIONAL_CODEC
        output.store("Items", ItemStack.OPTIONAL_CODEC.listOf(), this.stacks);
    }

    public void deserialize(ValueInput input) {
        java.util.List<ItemStack> list = new java.util.ArrayList<>();
        input.listOrEmpty("Items", ItemStack.OPTIONAL_CODEC).forEach(list::add);
        for (int i = 0; i < getSlots(); i++) {
            stacks.set(i, i < list.size() ? list.get(i) : ItemStack.EMPTY);
        }
    }
}
