package com.buwan.shizuku.crafting;

import com.buwan.shizuku.compat.RecipeMatcher;
import com.buwan.shizuku.registry.ModRecipeSerializers;
import com.buwan.shizuku.registry.ModRecipeTypes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 雫工作台的无序配方（shizuku:shapeless_table）。
 */
public class ShapelessTableCraftingRecipe implements ITierCraftingRecipe {

    private final NonNullList<Ingredient> inputs;
    private final ResourceLocation recipeId;
    private final ItemStack output;
    private final int tier;

    public ShapelessTableCraftingRecipe(ResourceLocation recipeId, NonNullList<Ingredient> inputs, ItemStack output) {
        this(recipeId, inputs, output, 0);
    }

    public ShapelessTableCraftingRecipe(ResourceLocation recipeId, NonNullList<Ingredient> inputs,
                                        ItemStack output, int tier) {
        this.recipeId = recipeId;
        this.inputs = inputs;
        this.output = output;
        this.tier = tier;
    }

    private static int getTierFromSize(int size) {
        return size < 10 ? 1 : (size < 26 ? 2 : (size < 50 ? 3 : 4));
    }

    @Override
    @NotNull
    public ItemStack getResultItem(@NotNull RegistryAccess access) {
        return this.output;
    }

    @Override
    @NotNull
    public NonNullList<Ingredient> getIngredients() {
        return this.inputs;
    }

    @Override
    @NotNull
    public ResourceLocation getId() {
        return this.recipeId;
    }

    @Override
    @NotNull
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.SHAPELESS_CRAFT_SERIALIZER.get();
    }

    @Override
    @NotNull
    public RecipeType<?> getType() {
        return ModRecipeTypes.CRAFTING_TABLE_RECIPE.get();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= this.inputs.size();
    }

    @Override
    @NotNull
    public ItemStack assemble(@NotNull Container inv, @NotNull RegistryAccess access) {
        return this.output.copy();
    }

    @Override
    public boolean matches(@NotNull Container inv, @NotNull Level level) {
        if (this.tier != 0 && this.tier != getTierFromSize(inv.getContainerSize())) {
            return false;
        }
        List<ItemStack> stacks = new ArrayList<>();
        int matched = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                stacks.add(stack);
                matched++;
            }
        }
        return matched == this.inputs.size() && RecipeMatcher.findMatches(stacks, this.inputs) != null;
    }

    @Override
    public int getTier() {
        return this.tier > 0 ? this.tier : getTierFromSize(this.inputs.size());
    }

    @Override
    public boolean hasRequiredTier() {
        return this.tier > 0;
    }

    public static class Serializer implements RecipeSerializer<ShapelessTableCraftingRecipe> {
        @Override
        @NotNull
        public ShapelessTableCraftingRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
            NonNullList<Ingredient> inputs = NonNullList.create();
            JsonArray ingredients = GsonHelper.getAsJsonArray(json, "ingredients");
            for (int i = 0; i < ingredients.size(); i++) {
                inputs.add(Ingredient.fromJson(ingredients.get(i)));
            }
            ItemStack output = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            int tier = GsonHelper.getAsInt(json, "tier", 0);
            return new ShapelessTableCraftingRecipe(recipeId, inputs, output, tier);
        }

        @Override
        public ShapelessTableCraftingRecipe fromNetwork(@NotNull ResourceLocation recipeId, FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            NonNullList<Ingredient> inputs = NonNullList.withSize(size, Ingredient.EMPTY);
            for (int i = 0; i < size; i++) {
                inputs.set(i, Ingredient.fromNetwork(buffer));
            }
            ItemStack output = buffer.readItem();
            int tier = buffer.readVarInt();
            return new ShapelessTableCraftingRecipe(recipeId, inputs, output, tier);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ShapelessTableCraftingRecipe recipe) {
            buffer.writeVarInt(recipe.inputs.size());
            for (Ingredient ingredient : recipe.inputs) {
                ingredient.toNetwork(buffer);
            }
            buffer.writeItem(recipe.output);
            buffer.writeVarInt(recipe.tier);
        }
    }
}
