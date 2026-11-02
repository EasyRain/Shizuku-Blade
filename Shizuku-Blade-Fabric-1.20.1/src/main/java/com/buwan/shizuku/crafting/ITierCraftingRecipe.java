package com.buwan.shizuku.crafting;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * 雫工作台自定义配方：带阶（tier）约束。
 * tier n 对应 (2n+1)x(2n+1) 的合成网格。
 */
public interface ITierCraftingRecipe extends Recipe<RecipeInput> {

    int getTier();

    boolean hasRequiredTier();
}
