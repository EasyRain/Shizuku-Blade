package com.buwan.shizuku.crafting;

import com.buwan.shizuku.registry.ModRecipeSerializers;
import com.buwan.shizuku.registry.ModRecipeTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import com.buwan.shizuku.compat.RecipeMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * 雫工作台的无序配方（shizuku:shapeless_table）。
 */
public class ShapelessTableCraftingRecipe implements ITierCraftingRecipe {

    private final NonNullList<Ingredient> inputs;
    private final ItemStack output;
    /** 从 JSON 解析时用模板暂存（加载期不能造 ItemStack）。 */
    private final net.minecraft.world.item.ItemStackTemplate outputTemplate;
    private final int tier;

    public ShapelessTableCraftingRecipe(NonNullList<Ingredient> inputs, ItemStack output, int tier) {
        this.inputs = inputs;
        this.output = output;
        this.tier = tier;
        this.outputTemplate = null;
    }

    private ShapelessTableCraftingRecipe(NonNullList<Ingredient> inputs,
                                          net.minecraft.world.item.ItemStackTemplate outputTemplate, int tier) {
        this.inputs = inputs;
        this.output = ItemStack.EMPTY;
        this.tier = tier;
        this.outputTemplate = outputTemplate;
    }

    private ItemStack outputStack() {
        if (this.outputTemplate != null) {
            return new ItemStack(this.outputTemplate.item(), this.outputTemplate.count(),
                    this.outputTemplate.components());
        }
        return this.outputStack();
    }

    private static int getTierFromSize(int size) {
        return size < 10 ? 1 : (size < 26 ? 2 : (size < 50 ? 3 : 4));
    }

    public ItemStack buildResult() {
        return this.output;
    }

    private NonNullList<Ingredient> inputs() {
        return this.inputs;
    }

    @Override
    public java.util.List<java.util.Optional<Ingredient>> ingredientSlots() {
        java.util.List<java.util.Optional<Ingredient>> slots = new java.util.ArrayList<>(this.inputs.size());
        for (Ingredient ingredient : this.inputs) {
            slots.add(java.util.Optional.of(ingredient));
        }
        return slots;
    }

    @Override
    public RecipeSerializer<? extends Recipe<RecipeInput>> getSerializer() {
        return ModRecipeSerializers.SHAPELESS_CRAFT_SERIALIZER;
    }

    @Override
    public RecipeType<? extends Recipe<RecipeInput>> getType() {
        return ModRecipeTypes.CRAFTING_TABLE_RECIPE.get();
    }

    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= this.inputs.size();
    }

    @Override
    public ItemStack assemble(RecipeInput input) {
        return this.outputStack().copy();
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public boolean showNotification() {
        return true;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(this.inputs);
    }

    @Override
    public boolean matches(RecipeInput inv, Level level) {
        if (this.tier != 0 && this.tier != getTierFromSize(inv.size())) {
            return false;
        }
        List<ItemStack> stacks = new ArrayList<>();
        int matched = 0;
        for (int i = 0; i < inv.size(); i++) {
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

    public static final class Serializer {

        public static final MapCodec<ShapelessTableCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Ingredient.CODEC.listOf().fieldOf("ingredients").forGetter(ShapelessTableCraftingRecipe::inputs),
                // 26.1：配方产物要用 ItemStackTemplate 解析（模板不校验物品 components）；
                // 直接用 ItemStack.CODEC 会在数据包加载期抛 "Item ... does not have components yet"
                net.minecraft.world.item.ItemStackTemplate.CODEC
                        .fieldOf("result")
                        .forGetter(r -> net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(r.output)),
                com.mojang.serialization.Codec.INT.optionalFieldOf("tier", 0).forGetter(r -> r.tier)
        ).apply(inst, (inputs, output, tier) -> {

            NonNullList<Ingredient> list = NonNullList.create();
            list.addAll(inputs);
            return new ShapelessTableCraftingRecipe(list, output, tier);
        }));

        public static final StreamCodec<RegistryFriendlyByteBuf, ShapelessTableCraftingRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.collection(NonNullList::createWithCapacity, Ingredient.CONTENTS_STREAM_CODEC), ShapelessTableCraftingRecipe::inputs,
                ItemStack.STREAM_CODEC, r -> r.output,
                ByteBufCodecs.VAR_INT, r -> r.tier,
                ShapelessTableCraftingRecipe::new
        );
    }
}