package com.buwan.shizuku.crafting;

import com.buwan.shizuku.registry.ModRecipeSerializers;
import com.buwan.shizuku.registry.ModRecipeTypes;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * 雫工作台的有序配方（shizuku:shaped_table）。
 * 支持 9x9 大网格内的平移 + 镜像匹配；产出可为指定命名刀（blade 字段）。
 */
public class ShapedTableCraftingRecipe implements ITierCraftingRecipe {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShapedTableCraftingRecipe.class);

    private final ResourceLocation recipeId;
    private final NonNullList<Ingredient> inputs;
    private final ItemStack output;
    private final int width;
    private final int height;
    private final int tier;
    @Nullable
    private final ResourceLocation outputBlade;

    public ShapedTableCraftingRecipe(ResourceLocation recipeId, int width, int height,
                                     NonNullList<Ingredient> inputs, ItemStack output) {
        this(recipeId, width, height, inputs, output, 0, null);
    }

    public ShapedTableCraftingRecipe(ResourceLocation recipeId, int width, int height,
                                     NonNullList<Ingredient> inputs, ItemStack output, int tier) {
        this(recipeId, width, height, inputs, output, tier, null);
    }

    public ShapedTableCraftingRecipe(ResourceLocation recipeId, int width, int height,
                                     NonNullList<Ingredient> inputs, ItemStack output, int tier,
                                     @Nullable ResourceLocation outputBlade) {
        this.recipeId = recipeId;
        this.inputs = inputs;
        this.output = output;
        this.width = width;
        this.height = height;
        this.tier = tier;
        this.outputBlade = outputBlade;
    }

    private static Map<Character, Ingredient> keyFromJson(JsonObject keyJson) {
        Map<Character, Ingredient> map = Maps.newHashMap();
        for (Map.Entry<String, JsonElement> entry : keyJson.entrySet()) {
            if (entry.getKey().length() != 1) {
                throw new JsonSyntaxException("Invalid key entry: '" + entry.getKey()
                        + "' is an invalid symbol (must be 1 character only).");
            }
            if (" ".equals(entry.getKey())) {
                throw new JsonSyntaxException("Invalid key entry: ' ' is a reserved symbol.");
            }
            map.put(entry.getKey().charAt(0), Ingredient.fromJson(entry.getValue()));
        }
        return map;
    }

    private static NonNullList<Ingredient> dissolvePattern(String[] pattern, Map<Character, Ingredient> key,
                                                           int width, int height) {
        NonNullList<Ingredient> inputs = NonNullList.withSize(width * height, Ingredient.EMPTY);
        Set<Character> set = Sets.newHashSet(key.keySet());
        set.remove(' ');
        for (int i = 0; i < pattern.length; i++) {
            for (int j = 0; j < pattern[i].length(); j++) {
                char c0 = pattern[i].charAt(j);
                if (c0 != ' ') {
                    Ingredient ingredient = key.get(c0);
                    if (ingredient == null) {
                        throw new JsonSyntaxException("Pattern references symbol '" + c0
                                + "' but it's not defined in the key");
                    }
                    set.remove(c0);
                    inputs.set(j + width * i, ingredient);
                }
            }
        }
        if (!set.isEmpty()) {
            throw new JsonSyntaxException("Key defines symbols that aren't used in pattern: " + set);
        }
        return inputs;
    }

    private static String[] patternFromJson(JsonArray jsonArr) {
        String[] astring = new String[jsonArr.size()];
        for (int i = 0; i < astring.length; i++) {
            String s = GsonHelper.getAsString(jsonArr.get(i), "pattern[" + i + "]");
            if (i > 0 && astring[0].length() != s.length()) {
                throw new JsonSyntaxException("Invalid pattern: each row must be the same width");
            }
            astring[i] = s;
        }
        return astring;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    @Override
    @NotNull
    public ItemStack getResultItem(@NotNull RegistryAccess access) {
        if (this.outputBlade != null) {
            try {
                Registry<SlashBladeDefinition> registry = access.registryOrThrow(SlashBladeDefinition.REGISTRY_KEY);
                SlashBladeDefinition holder = registry.get(this.outputBlade);
                if (holder != null) {
                    return holder.getBlade();
                }
                LOGGER.error("[ShizukuCraft] Blade definition '{}' not found in registry {}",
                        this.outputBlade, SlashBladeDefinition.REGISTRY_KEY.location());
            } catch (Exception e) {
                LOGGER.error("[ShizukuCraft] Failed to create output blade '{}'", this.outputBlade, e);
            }
            return this.output.copy();
        }
        return this.output;
    }

    @Override
    @NotNull
    public ItemStack assemble(@NotNull Container inv, @NotNull RegistryAccess access) {
        return this.getResultItem(access).copy();
    }

    @Override
    public boolean matches(@NotNull Container inv, @NotNull Level level) {
        if (this.tier != 0 && this.tier != this.getTierFromGridSize(inv.getContainerSize())) {
            return false;
        }
        int size = (int) Math.sqrt(inv.getContainerSize());
        for (int i = 0; i <= size - this.width; i++) {
            for (int j = 0; j <= size - this.height; j++) {
                if (this.checkMatch(inv, i, j, true)) {
                    return true;
                }
                if (this.checkMatch(inv, i, j, false)) {
                    return true;
                }
            }
        }
        return false;
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
        return ModRecipeSerializers.SHAPED_CRAFT_SERIALIZER.get();
    }

    @Override
    @NotNull
    public RecipeType<?> getType() {
        return ModRecipeTypes.CRAFTING_TABLE_RECIPE.get();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= this.width && height >= this.height;
    }

    @Override
    public int getTier() {
        if (this.tier > 0) {
            return this.tier;
        }
        return this.width < 4 && this.height < 4 ? 1
                : (this.width < 6 && this.height < 6 ? 2
                : (this.width < 8 && this.height < 8 ? 3 : 4));
    }

    @Override
    public boolean hasRequiredTier() {
        return this.tier > 0;
    }

    private int getTierFromGridSize(int slots) {
        return slots < 10 ? 1 : (slots < 26 ? 2 : (slots < 50 ? 3 : 4));
    }

    private boolean checkMatch(Container inventory, int x, int y, boolean mirror) {
        int size = (int) Math.sqrt(inventory.getContainerSize());
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int k = i - x;
                int l = j - y;
                Ingredient ingredient = Ingredient.EMPTY;
                if (k >= 0 && l >= 0 && k < this.width && l < this.height) {
                    ingredient = mirror
                            ? this.inputs.get(this.width - k - 1 + l * this.width)
                            : this.inputs.get(k + l * this.width);
                }
                if (!ingredient.test(inventory.getItem(i + j * size))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static class Serializer implements RecipeSerializer<ShapedTableCraftingRecipe> {
        @Override
        @NotNull
        public ShapedTableCraftingRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
            Map<Character, Ingredient> map = ShapedTableCraftingRecipe.keyFromJson(GsonHelper.getAsJsonObject(json, "key"));
            String[] pattern = ShapedTableCraftingRecipe.patternFromJson(GsonHelper.getAsJsonArray(json, "pattern"));
            int width = pattern[0].length();
            int height = pattern.length;
            NonNullList<Ingredient> inputs = ShapedTableCraftingRecipe.dissolvePattern(pattern, map, width, height);
            ItemStack output = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            int tier = GsonHelper.getAsInt(json, "tier", 0);
            int size = tier * 2 + 1;
            if (tier == 0 || width <= size && height <= size) {
                ResourceLocation outputBlade = json.has("blade")
                        ? new ResourceLocation(GsonHelper.getAsString(json, "blade"))
                        : null;
                return new ShapedTableCraftingRecipe(recipeId, width, height, inputs, output, tier, outputBlade);
            }
            throw new JsonSyntaxException("The pattern size is larger than the specified tier can support");
        }

        @Override
        public ShapedTableCraftingRecipe fromNetwork(@NotNull ResourceLocation recipeId, FriendlyByteBuf buffer) {
            int width = buffer.readVarInt();
            int height = buffer.readVarInt();
            NonNullList<Ingredient> inputs = NonNullList.withSize(width * height, Ingredient.EMPTY);
            inputs.replaceAll(ignored -> Ingredient.fromNetwork(buffer));
            ItemStack output = buffer.readItem();
            int tier = buffer.readVarInt();
            ResourceLocation outputBlade = null;
            if (buffer.readBoolean()) {
                outputBlade = buffer.readResourceLocation();
            }
            return new ShapedTableCraftingRecipe(recipeId, width, height, inputs, output, tier, outputBlade);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ShapedTableCraftingRecipe recipe) {
            buffer.writeVarInt(recipe.width);
            buffer.writeVarInt(recipe.height);
            for (Ingredient ingredient : recipe.inputs) {
                Ingredient.toNetwork(buffer, ingredient);
            }
            buffer.writeItem(recipe.output);
            buffer.writeVarInt(recipe.tier);
            buffer.writeBoolean(recipe.outputBlade != null);
            if (recipe.outputBlade != null) {
                buffer.writeResourceLocation(recipe.outputBlade);
            }
        }
    }
}
