package com.buwan.shizuku.registry;

import com.buwan.shizuku.compat.DeferredRegister;
import com.buwan.shizuku.crafting.ITierCraftingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.function.Supplier;

public class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPES =
            DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, "shizuku");

    public static final Supplier<RecipeType<ITierCraftingRecipe>> CRAFTING_TABLE_RECIPE =
            RECIPES.register("crafting_table_recipe", () -> new RecipeType<ITierCraftingRecipe>() { });
}
