package com.buwan.shizuku.registry;

import com.buwan.shizuku.compat.DeferredRegister;
import com.buwan.shizuku.crafting.ShapedTableCraftingRecipe;
import com.buwan.shizuku.crafting.ShapelessTableCraftingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.function.Supplier;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, "shizuku");

    // 26.1：RecipeSerializer 是 record(MapCodec, StreamCodec)，不再实现接口
    public static final Supplier<RecipeSerializer<?>> SHAPED_CRAFT_SERIALIZER =
            SERIALIZERS.register("shaped_table",
                    () -> new RecipeSerializer<>(ShapedTableCraftingRecipe.Serializer.CODEC,
                            ShapedTableCraftingRecipe.Serializer.STREAM_CODEC));

    public static final Supplier<RecipeSerializer<?>> SHAPELESS_CRAFT_SERIALIZER =
            SERIALIZERS.register("shapeless_table",
                    () -> new RecipeSerializer<>(ShapelessTableCraftingRecipe.Serializer.CODEC,
                            ShapelessTableCraftingRecipe.Serializer.STREAM_CODEC));
}
