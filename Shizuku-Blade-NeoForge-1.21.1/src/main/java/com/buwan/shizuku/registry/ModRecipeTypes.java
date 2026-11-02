package com.buwan.shizuku.registry;

import com.buwan.shizuku.crafting.ITierCraftingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_TYPE, "shizuku");

    public static final Supplier<RecipeType<ITierCraftingRecipe>> CRAFTING_TABLE_RECIPE = RECIPES.register(
            "crafting_table_recipe",
            () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath("shizuku", "crafting_table_recipe"))
    );
}
