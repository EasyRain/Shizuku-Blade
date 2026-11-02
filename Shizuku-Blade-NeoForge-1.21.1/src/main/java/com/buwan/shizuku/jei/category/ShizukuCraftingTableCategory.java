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
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
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
                // 产物同样要规范化：直接 getResultItem 会带出旧注册表的附魔 Holder
                .addItemStack(com.buwan.shizuku.util.BladeStacks.canonical(recipe.getResultItem(access), access));
    }

    /** {@code SlashBladeIngredient#request}（私有 record 字段，无 getter）。 */
    private static final java.lang.reflect.Field REQUEST_FIELD = findRequestField();

    private static java.lang.reflect.Field findRequestField() {
        Class<?> type = mods.flammpfeil.slashblade.recipe.SlashBladeIngredient.class;
        while (type != null && type != Object.class) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (mods.flammpfeil.slashblade.recipe.RequestDefinition.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        return field;
                    } catch (Throwable ignored) {
                        return null;
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static mods.flammpfeil.slashblade.recipe.RequestDefinition requestOf(Ingredient ingredient) {
        Object custom = ingredient.getCustomIngredient();
        if (custom == null || REQUEST_FIELD == null) {
            return null;
        }
        try {
            Object value = REQUEST_FIELD.get(custom);
            return value instanceof mods.flammpfeil.slashblade.recipe.RequestDefinition def ? def : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void addIngredientSlot(IRecipeLayoutBuilder builder, Ingredient ingredient,
                                          int x, int y, HolderLookup.Provider access) {
        var slot = builder.addSlot(RecipeIngredientRole.INPUT, x, y);
        mods.flammpfeil.slashblade.recipe.RequestDefinition request = requestOf(ingredient);
        ItemStack display = resolveBladeFor(request, access);
        if (display.isEmpty()) {
            slot.addIngredients(ingredient);
            return;
        }
        slot.addItemStack(display);
        if (request != null) {
            addRequirementTooltip(slot, request);
        }
    }

    /**
     * 按配方要求取真实的命名刀（含完整默认属性）用于展示。
     *
     * <p>直接按定义注册表键（= 定义 JSON 文件名）查。约定：命名刀的定义文件名必须与
     * {@code name} 字段一致；不一致时按 id 查不到，图标会退化成默认模型
     * （历史遗留的稻光/有名已统一改名修正，因此不再做二级兜底查找）。</p>
     *
     * <p>返回完整定义刀而不是"只补模型贴图"的壳，这样能看到刀的默认属性。</p>
     */
    private static ItemStack resolveBladeFor(mods.flammpfeil.slashblade.recipe.RequestDefinition request,
                                             HolderLookup.Provider access) {
        if (request == null || request.name() == null) {
            return ItemStack.EMPTY;
        }
        return access.lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                .get(net.minecraft.resources.ResourceKey.create(
                        SlashBladeDefinition.REGISTRY_KEY, request.name()))
                .map(holder -> com.buwan.shizuku.util.BladeStacks.canonical(holder.value().getBlade(access), access))
                .orElse(ItemStack.EMPTY);
    }

    private static void addRequirementTooltip(mezz.jei.api.gui.builder.IRecipeSlotBuilder slot,
                                              mods.flammpfeil.slashblade.recipe.RequestDefinition request) {
        List<Component> lines = new java.util.ArrayList<>();
        if (request.killCount() > 0) {
            lines.add(Component.translatable("jei.shizuku.requirement.kill", request.killCount()));
        }
        if (request.proudSoulCount() > 0) {
            lines.add(Component.translatable("jei.shizuku.requirement.proud_soul", request.proudSoulCount()));
        }
        if (request.refineCount() > 0) {
            lines.add(Component.translatable("jei.shizuku.requirement.refine", request.refineCount()));
        }
        for (var enchantment : request.enchantments()) {
            // NeoForge 的 EnchantmentDefinition 只暴露 Holder<Enchantment>，用官方 API 取"锋利 V"这种全名
            lines.add(Component.translatable("jei.shizuku.requirement.enchant",
                    net.minecraft.world.item.enchantment.Enchantment.getFullname(
                            enchantment.getEnchantment(), enchantment.getEnchantmentLevel())));
        }
        if (lines.isEmpty()) {
            return;
        }
        slot.addRichTooltipCallback((view, tooltip) -> tooltip.addAll(lines));
    }

    @Override
    public void draw(ITierCraftingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        if (recipe instanceof ShapedTableCraftingRecipe) {
            // 形状提示：标题旁显示图案尺寸
        }
    }
}
