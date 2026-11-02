package com.buwan.shizuku.container.slot;

import com.buwan.shizuku.registry.ModRecipeTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.NotNull;

/**
 * 雫工作台结果槽：仿原版 ResultSlot，onTake 时结算剩余物并触发重合成。
 */
public class ModCraftResultSlot extends Slot {

    private final AbstractContainerMenu container;
    private final CraftingContainer craftSlots;
    private final Player player;
    private int removeCount;

    public ModCraftResultSlot(Player player, AbstractContainerMenu menu, CraftingContainer craftSlots,
                              Container container, int index, int xPosition, int yPosition) {
        super(container, index, xPosition, yPosition);
        this.player = player;
        this.container = menu;
        this.craftSlots = craftSlots;
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    @NotNull
    public ItemStack remove(int amount) {
        if (this.hasItem()) {
            this.removeCount += Math.min(amount, this.getItem().getCount());
        }
        return super.remove(amount);
    }

    @Override
    protected void onQuickCraft(@NotNull ItemStack stack, int amount) {
        this.removeCount += amount;
        this.checkTakeAchievements(stack);
    }

    @Override
    protected void onSwapCraft(int numItemsCrafted) {
        this.removeCount += numItemsCrafted;
    }

    @Override
    protected void checkTakeAchievements(ItemStack stack) {
        if (this.removeCount > 0) {
            stack.onCraftedBy(this.player.level(), this.player, this.removeCount);
            EventHooks.firePlayerCraftingEvent(this.player, stack, this.craftSlots);
        }
        this.removeCount = 0;
    }

    @Override
    public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
        this.checkTakeAchievements(stack);
        CommonHooks.setCraftingPlayer(player);
        NonNullList<ItemStack> remaining = player.level()
                .getRecipeManager()
                .getRemainingItemsFor(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get(), this.craftSlots.asCraftInput(), player.level());
        CommonHooks.setCraftingPlayer(null);

        for (int i = 0; i < remaining.size(); i++) {
            ItemStack slotStack = this.craftSlots.getItem(i);
            ItemStack remainingStack = remaining.get(i);
            if (!slotStack.isEmpty()) {
                this.craftSlots.removeItem(i, 1);
                slotStack = this.craftSlots.getItem(i);
            }

            if (!remainingStack.isEmpty()) {
                if (slotStack.isEmpty()) {
                    this.craftSlots.setItem(i, remainingStack);
                } else if (ItemStack.isSameItem(slotStack, remainingStack) && ItemStack.isSameItemSameComponents(slotStack, remainingStack)) {
                    remainingStack.grow(slotStack.getCount());
                    this.craftSlots.setItem(i, remainingStack);
                } else if (!this.player.getInventory().add(remainingStack)) {
                    this.player.drop(remainingStack, false);
                }
            }
        }

        this.container.slotsChanged(this.craftSlots);
    }
}
