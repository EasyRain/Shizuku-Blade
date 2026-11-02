package com.buwan.shizuku.container;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 包装 ItemStackHandler 的合成矩阵，任何写入都触发菜单重新合成。
 */
public class ModCraftContainer extends TransientCraftingContainer {

    private final AbstractContainerMenu menu;
    private final ItemStackHandler inventory;

    public ModCraftContainer(AbstractContainerMenu menu, ItemStackHandler inventory, int size) {
        // 1.21.1：CraftingContainer.asCraftInput() 用 width/height 构造 CraftingInput，
        // 必须报 9x9（81 格）；1.20.1 传 (size,size) 的写法在 1.21.1 会产生 81x81 的输入维度。
        super(menu, 9, 9);
        this.menu = menu;
        this.inventory = inventory;
    }

    @Override
    public int getContainerSize() {
        return this.inventory.getSlots();
    }

    /**
     * 关键：TransientCraftingContainer.getItems() 默认返回其内部（空的）列表，
     * 而 asCraftInput() 用它构造 CraftingInput——必须返回 handler 的真实内容。
     */
    @Override
    public List<ItemStack> getItems() {
        List<ItemStack> items = new java.util.ArrayList<>(this.inventory.getSlots());
        for (int i = 0; i < this.inventory.getSlots(); i++) {
            items.add(this.inventory.getStackInSlot(i));
        }
        return items;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < this.getContainerSize(); i++) {
            if (!this.inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    @NotNull
    public ItemStack getItem(int slot) {
        return this.inventory.getStackInSlot(slot);
    }

    @Override
    @NotNull
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = this.inventory.extractItem(slot, amount, false);
        this.menu.slotsChanged(this);
        return stack;
    }

    @Override
    @NotNull
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = this.inventory.getStackInSlot(slot);
        this.inventory.setStackInSlot(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        this.inventory.setStackInSlot(slot, stack);
        this.menu.slotsChanged(this);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < this.getContainerSize(); i++) {
            this.inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }
}
