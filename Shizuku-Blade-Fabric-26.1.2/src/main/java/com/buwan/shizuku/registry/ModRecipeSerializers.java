package com.buwan.shizuku.registry;

import com.buwan.shizuku.crafting.ShapedTableCraftingRecipe;
import com.buwan.shizuku.crafting.ShapelessTableCraftingRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * 26.1：RecipeSerializer 是 record(MapCodec, StreamCodec)，注册用 Registry.register。
 */
public final class ModRecipeSerializers {

    private ModRecipeSerializers() {
    }

    public static final RecipeSerializer<ShapedTableCraftingRecipe> SHAPED_CRAFT_SERIALIZER =
            new RecipeSerializer<>(ShapedTableCraftingRecipe.Serializer.CODEC,
                    ShapedTableCraftingRecipe.Serializer.STREAM_CODEC);

    public static final RecipeSerializer<ShapelessTableCraftingRecipe> SHAPELESS_CRAFT_SERIALIZER =
            new RecipeSerializer<>(ShapelessTableCraftingRecipe.Serializer.CODEC,
                    ShapelessTableCraftingRecipe.Serializer.STREAM_CODEC);

    public static void registerAll() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                Identifier.fromNamespaceAndPath("shizuku", "shaped_table"), SHAPED_CRAFT_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                Identifier.fromNamespaceAndPath("shizuku", "shapeless_table"), SHAPELESS_CRAFT_SERIALIZER);
    }
}
