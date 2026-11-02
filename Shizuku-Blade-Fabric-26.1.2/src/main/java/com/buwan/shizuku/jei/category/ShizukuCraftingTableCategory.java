package com.buwan.shizuku.jei.category;

import com.buwan.shizuku.crafting.ITierCraftingRecipe;
import com.buwan.shizuku.crafting.ShapedTableCraftingRecipe;
import com.buwan.shizuku.crafting.ShapelessTableCraftingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 雫工作台 JEI 分类：9x9 输入网格 + 输出槽。
 */
public class ShizukuCraftingTableCategory extends AbstractRecipeCategory<ITierCraftingRecipe> {

    public static final RecipeType<ITierCraftingRecipe> RECIPE_TYPE =
            RecipeType.create("shizuku", "shizuku_crafting_table_recipe", ITierCraftingRecipe.class);

    public static final Identifier GUI_TEXTURE =
            Identifier.fromNamespaceAndPath("shizuku", "textures/gui/jei/shizuku_crafting_jei.png");

    private static final int GUI_WIDTH = 189;
    private static final int GUI_HEIGHT = 163;
    private static final int OUTPUT_X = 167;
    private static final int OUTPUT_Y = 74;

    private final IDrawable background;
    private final IDrawable icon;

    public ShizukuCraftingTableCategory(IGuiHelper guiHelper) {
        super(RECIPE_TYPE,
                Component.translatable("jei.category.shizuku.shizuku_crafting_table"),
                guiHelper.createDrawableItemStack(new ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                Identifier.fromNamespaceAndPath("shizuku", "shizuku_crafting_table"))
                                .orElseThrow().value())),
                GUI_WIDTH, GUI_HEIGHT);
        this.background = guiHelper.createDrawable(GUI_TEXTURE, 0, 0, GUI_WIDTH, GUI_HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        Identifier.fromNamespaceAndPath("shizuku", "shizuku_crafting_table"))
                        .orElseThrow().value()));
    }

    // 26.1 的 IRecipeCategory 移除了 getBackground()（背景改由 createRecipeExtras 提供）
    /**
     * 26.1 的 {@code IRecipeCategory} 删掉了 {@code getBackground()}，背景必须在
     * {@code createRecipeExtras} 里作为 drawable 加进去 —— 否则整张九宫格框线都不会绘制
     * （表现为"物品浮在 JEI 默认边框上、没有格子"）。
     */
    @Override
    public void createRecipeExtras(@NotNull mezz.jei.api.gui.widgets.IRecipeExtrasBuilder builder,
                                   @NotNull ITierCraftingRecipe recipe, @NotNull IFocusGroup focuses) {
        builder.addDrawable(this.background, 0, 0);
    }

    @NotNull
    public IDrawable getBackground() {
        return this.background;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull ITierCraftingRecipe recipe, @NotNull IFocusGroup focuses) {
        List<java.util.Optional<Ingredient>> slots = recipe.ingredientSlots();

        if (recipe instanceof ShapedTableCraftingRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            int offset = Math.floorDiv(9 - width, 2);
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    Ingredient ingredient = slots.get(j + i * width).orElse(null);
                    if (ingredient != null) {
                        builder.addSlot(RecipeIngredientRole.INPUT,
                                        18 * (j + offset) + 1, 18 * (i + offset) + 1)
                                .addIngredients(ingredient);
                    }
                }
            }
        } else if (recipe instanceof ShapelessTableCraftingRecipe) {
            int index = 0;
            for (java.util.Optional<Ingredient> slot : slots) {
                if (slot.isPresent()) {
                    builder.addSlot(RecipeIngredientRole.INPUT,
                                    18 * (index % 9) + 1, 18 * (index / 9) + 1)
                            .addIngredients(slot.get());
                    index++;
                }
            }
        }

        ItemStack result;
        if (recipe instanceof ShapedTableCraftingRecipe shaped) {
            result = shaped.buildResult(Minecraft.getInstance().level.registryAccess());
        } else if (recipe instanceof ShapelessTableCraftingRecipe shapeless) {
            result = shapeless.buildResult();
        } else {
            result = ItemStack.EMPTY;
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .addItemStack(result);
    }

    /**
     * 26.1 移除了 Ingredient#getItems()/getCustomIngredient()，命名刀的展示交给 JEI 的配料渲染。
     */

    @Override
    public void draw(ITierCraftingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor guiGraphics,
                     double mouseX, double mouseY) {
        if (recipe instanceof ShapedTableCraftingRecipe) {
            // 形状提示：标题旁显示图案尺寸
        }
    }
}
