package com.buwan.shizuku.crafting;

import com.buwan.shizuku.registry.ModRecipeSerializers;
import com.buwan.shizuku.registry.ModRecipeTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 雫工作台的有序配方（shizuku:shaped_table）。
 * 支持 9x9 大网格内的平移 + 镜像匹配；产出可为指定命名刀（blade 字段）。
 */
public class ShapedTableCraftingRecipe implements ITierCraftingRecipe {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShapedTableCraftingRecipe.class);

    private final NonNullList<Ingredient> inputs;
    private final ItemStack output;
    private final int width;
    private final int height;
    private final int tier;
    @Nullable
    private final Identifier outputBlade;

    public ShapedTableCraftingRecipe(int width, int height, NonNullList<Ingredient> inputs, ItemStack output,
                                     int tier, @Nullable Identifier outputBlade) {
        this.inputs = inputs;
        this.output = output;
        this.width = width;
        this.height = height;
        this.tier = tier;
        this.outputBlade = outputBlade;
    }

    /** 由 pattern/key JSON 数据溶解出 1.21.1 配方实例。 */
    public static ShapedTableCraftingRecipe fromPatternData(PatternData data, ItemStack output,
                                                            int tier, @Nullable Identifier outputBlade) {
        List<String> rows = data.rows();
        int width = rows.isEmpty() ? 0 : rows.get(0).length();
        int height = rows.size();
        NonNullList<Ingredient> inputs = NonNullList.withSize(width * height, ITierCraftingRecipe.EMPTY);
        for (int i = 0; i < height; i++) {
            String row = rows.get(i);
            for (int j = 0; j < row.length(); j++) {
                char c = row.charAt(j);
                if (c != ' ') {
                    Ingredient ingredient = data.key().get(String.valueOf(c));
                    if (ingredient == null) {
                        throw new IllegalStateException("Pattern references symbol '" + c + "' but it's not defined in the key; parsed keys=" + data.key().keySet() + ", rows=" + rows);
                    }
                    inputs.set(j + width * i, ingredient);
                }
            }
        }
        return new ShapedTableCraftingRecipe(width, height, inputs, output, tier, outputBlade);
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public ItemStack buildResult(RegistryAccess access) {
        if (this.outputBlade != null) {
            try {
                SlashBladeDefinition definition = access.lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                        .getOrThrow(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY, this.outputBlade))
                        .value();
                if (definition != null) {
                    return definition.getBlade();
                }
                LOGGER.error("[ShizukuCraft] Blade definition '{}' not found in registry {}", this.outputBlade,
                        SlashBladeDefinition.REGISTRY_KEY.identifier());
            } catch (Exception e) {
                LOGGER.error("[ShizukuCraft] Failed to create output blade '{}'", this.outputBlade, e);
            }
            return this.output.copy();
        }
        return this.output;
    }

    @Override
    public ItemStack assemble(RecipeInput input) {
        RegistryAccess access = com.buwan.shizuku.network.PacketHandler.registryAccess();
        return (access != null ? this.buildResult(access) : this.output).copy();
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
        if (this.tier != 0 && this.tier != getTierFromGridSize(inv.size())) {
            return false;
        }
        int size = (int) Math.sqrt(inv.size());
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
    public NonNullList<Ingredient> getIngredients() {
        return this.inputs;
    }

    @Override
    public RecipeSerializer<? extends Recipe<RecipeInput>> getSerializer() {
        return ModRecipeSerializers.SHAPED_CRAFT_SERIALIZER;
    }

    @Override
    public RecipeType<? extends Recipe<RecipeInput>> getType() {
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

    private boolean checkMatch(RecipeInput inventory, int x, int y, boolean mirror) {
        int size = (int) Math.sqrt(inventory.size());
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int k = i - x;
                int l = j - y;
                Ingredient ingredient = ITierCraftingRecipe.EMPTY;
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

    public record PatternData(List<String> rows, Map<String, Ingredient> key) {
        public static final MapCodec<PatternData> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                net.minecraft.util.ExtraCodecs.nonEmptyList(CodecHolder.STRING_LIST).fieldOf("pattern").forGetter(PatternData::rows),
                CodecHolder.STRING_INGREDIENT_MAP.fieldOf("key").forGetter(PatternData::key)
        ).apply(inst, PatternData::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, PatternData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PatternData::rows,
                ByteBufCodecs.map(java.util.HashMap::new, ByteBufCodecs.STRING_UTF8, Ingredient.CONTENTS_STREAM_CODEC), PatternData::key,
                PatternData::new
        );
    }

    /** 避免直接引用 Codec/StreamCodec 静态字段的初始化顺序问题的小容器。 */
    static final class CodecHolder {
        static final com.mojang.serialization.Codec<java.util.List<String>> STRING_LIST =
                com.mojang.serialization.Codec.STRING.listOf();
        static final com.mojang.serialization.Codec<Map<String, Ingredient>> STRING_INGREDIENT_MAP =
                net.minecraft.util.ExtraCodecs.strictUnboundedMap(com.mojang.serialization.Codec.STRING, Ingredient.CODEC);
    }

    public static final class Serializer {

        public static final MapCodec<ShapedTableCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                PatternData.CODEC.forGetter(r -> new PatternData(rowsOf(r), keyOf(r))),
                ItemStack.CODEC.fieldOf("result").forGetter(r -> r.output),
                com.mojang.serialization.Codec.INT.optionalFieldOf("tier", 0).forGetter(r -> r.tier),
                Identifier.CODEC.optionalFieldOf("blade").forGetter(r -> Optional.ofNullable(r.outputBlade))
        ).apply(inst, (data, output, tier, blade) -> fromPatternData(data, output, tier, blade.orElse(null))));

        public static final StreamCodec<RegistryFriendlyByteBuf, ShapedTableCraftingRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ShapedTableCraftingRecipe::getWidth,
                ByteBufCodecs.VAR_INT, ShapedTableCraftingRecipe::getHeight,
                ByteBufCodecs.collection(NonNullList::createWithCapacity, Ingredient.CONTENTS_STREAM_CODEC), ShapedTableCraftingRecipe::getIngredients,
                ItemStack.STREAM_CODEC, r -> r.output,
                ByteBufCodecs.VAR_INT, r -> r.tier,
                ByteBufCodecs.optional(Identifier.STREAM_CODEC), r -> Optional.ofNullable(r.outputBlade),
                (width, height, inputs, output, tier, blade) ->
                        new ShapedTableCraftingRecipe(width, height, inputs, output, tier, blade.orElse(null))
        );
    }

    private static List<String> rowsOf(ShapedTableCraftingRecipe r) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < r.height; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < r.width; j++) {
                sb.append(' ');
            }
            rows.add(sb.toString());
        }
        return rows;
    }

    private static Map<String, Ingredient> keyOf(ShapedTableCraftingRecipe r) {
        return Map.of();
    }
}
