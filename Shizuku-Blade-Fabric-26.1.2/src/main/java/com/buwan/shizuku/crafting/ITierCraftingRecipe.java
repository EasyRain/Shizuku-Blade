package com.buwan.shizuku.crafting;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.ItemLike;

/**
 * 雫工作台自定义配方：带阶（tier）约束。
 * tier n 对应 (2n+1)x(2n+1) 的合成网格。
 */
public interface ITierCraftingRecipe extends Recipe<RecipeInput> {

    /** 26.1 移除了 {@code ITierCraftingRecipe.EMPTY}，用空数组构造一个空配料。 */
    Ingredient EMPTY = Ingredient.of(new ItemLike[0]);

    int getTier();

    boolean hasRequiredTier();
}
