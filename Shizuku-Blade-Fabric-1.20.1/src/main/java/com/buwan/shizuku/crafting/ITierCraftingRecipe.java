package com.buwan.shizuku.crafting;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.NotNull;

/**
 * 雫工作台自定义配方：带阶（tier）约束。
 * tier n 对应 (2n+1)x(2n+1) 的合成网格。
 */
public interface ITierCraftingRecipe extends Recipe<Container> {

    int getTier();

    boolean hasRequiredTier();

    @NotNull
    @Override
    default NonNullList<ItemStack> getRemainingItems(@NotNull Container inv) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < remaining.size(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem().hasCraftingRemainingItem()) {
                remaining.set(i, new ItemStack(stack.getItem().getCraftingRemainingItem()));
            }
        }
        return remaining;
    }
}
