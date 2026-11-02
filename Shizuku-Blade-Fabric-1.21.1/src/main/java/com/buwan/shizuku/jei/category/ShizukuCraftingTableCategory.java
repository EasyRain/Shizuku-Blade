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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    public static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("shizuku", "textures/gui/jei/shizuku_crafting_jei.png");

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
                                ResourceLocation.fromNamespaceAndPath("shizuku", "shizuku_crafting_table")))),
                GUI_WIDTH, GUI_HEIGHT);
        this.background = guiHelper.createDrawable(GUI_TEXTURE, 0, 0, GUI_WIDTH, GUI_HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("shizuku", "shizuku_crafting_table"))));
    }

    @Override
    public @NotNull IDrawable getBackground() {
        return this.background;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull ITierCraftingRecipe recipe, @NotNull IFocusGroup focuses) {
        List<Ingredient> inputs = recipe.getIngredients();
        HolderLookup.Provider access = Minecraft.getInstance().level.registryAccess();

        if (recipe instanceof ShapedTableCraftingRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            int offset = Math.floorDiv(9 - width, 2);
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    Ingredient ingredient = inputs.get(j + i * width);
                    if (!ingredient.isEmpty()) {
                        addIngredientSlot(builder, ingredient,
                                18 * (j + offset) + 1, 18 * (i + offset) + 1, access);
                    }
                }
            }
        } else if (recipe instanceof ShapelessTableCraftingRecipe) {
            int index = 0;
            for (Ingredient ingredient : inputs) {
                if (!ingredient.isEmpty()) {
                    addIngredientSlot(builder, ingredient, 18 * (index % 9) + 1, 18 * (index / 9) + 1, access);
                    index++;
                }
            }
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .addItemStack(recipe.getResultItem(access));
    }

    /**
     * 修复 JEI 中命名刀显示为"无名无铭"样式的问题：
     * 拔刀剑配料（slashblade:blade）的 getItems() 只带名字与属性、不带模型/材质，
     * 这里按 translationKey 匹配 SlashBladeDefinition，取完整命名刀（含模型与贴图）用于展示。
     */
    private static void addIngredientSlot(IRecipeLayoutBuilder builder, Ingredient ingredient,
                                          int x, int y, HolderLookup.Provider access) {
        var slot = builder.addSlot(RecipeIngredientRole.INPUT, x, y);
        if (ingredient.getCustomIngredient() != null) {
            ItemStack display = resolveNamedBlade(ingredient, access);
            if (!display.isEmpty()) {
                slot.addItemStack(display);
                return;
            }
        }
        slot.addIngredients(ingredient);
    }

    private static ItemStack resolveNamedBlade(Ingredient ingredient, HolderLookup.Provider access) {
        ItemStack[] items = ingredient.getItems();
        ItemStack plain = items.length > 0 ? items[0] : ItemStack.EMPTY;
        if (plain.getItem() instanceof ItemSlashBlade) {
            String key = CapabilitySlashBlade.getBladeState(plain).map(ISlashBladeState::getTranslationKey).orElse("");
            if (!key.isBlank()) {
                for (Holder.Reference<SlashBladeDefinition> ref :
                        access.lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().toList()) {
                    ItemStack defStack = ref.value().getBlade();
                    String defKey = CapabilitySlashBlade.getBladeState(defStack).map(ISlashBladeState::getTranslationKey).orElse("");
                    if (key.equals(defKey)) {
                        // 配方实际只要求刀名：展示栈 = 配料栈（仅名字）+ 定义中的模型/材质，
                        // 不附加定义的附魔/属性，避免 JEI 暗示存在属性要求。
                        ItemStack display = plain.copy();
                        CapabilitySlashBlade.getBladeState(defStack).ifPresent(defState ->
                                CapabilitySlashBlade.getBladeState(display).ifPresent(displayState -> {
                                    defState.getModel().ifPresent(displayState::setModel);
                                    defState.getTexture().ifPresent(displayState::setTexture);
                                }));
                        return display;
                    }
                }
            }
        }
        return plain;
    }

    @Override
    public void draw(ITierCraftingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        if (recipe instanceof ShapedTableCraftingRecipe) {
            // 形状提示：标题旁显示图案尺寸
        }
    }
}
