package com.buwan.shizuku.registry;

import com.buwan.shizuku.crafting.ShapedTableCraftingRecipe;
import com.buwan.shizuku.crafting.ShapelessTableCraftingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, "shizuku");

    public static final Supplier<RecipeSerializer<?>> SHAPED_CRAFT_SERIALIZER =
            SERIALIZERS.register("shaped_table", ShapedTableCraftingRecipe.Serializer::new);

    public static final Supplier<RecipeSerializer<?>> SHAPELESS_CRAFT_SERIALIZER =
            SERIALIZERS.register("shapeless_table", ShapelessTableCraftingRecipe.Serializer::new);
}
