package com.buwan.shizuku.crafting;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * 雫工作台自定义配方：带阶（tier）约束。
 * tier n 对应 (2n+1)x(2n+1) 的合成网格。
 */
public interface ITierCraftingRecipe extends Recipe<RecipeInput> {

    /**
     * 26.1 起 {@link Ingredient} 不允许为空（也不能含空气），空槽必须用 {@link java.util.Optional} 表示，
     * 对应原版 {@code PlacementInfo.createFromOptionals(...)} 的做法。
     */

    int getTier();

    boolean hasRequiredTier();

    /** 26.1 的 Recipe 接口改用 placementInfo(); 这里保留一个按格位的只读视图给 JEI 用。 */
    java.util.List<java.util.Optional<Ingredient>> ingredientSlots();
}
