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
import mods.flammpfeil.slashblade.recipe.RequestDefinition;
import mods.flammpfeil.slashblade.recipe.SlashBladeIngredient;
import mods.flammpfeil.slashblade.registry.slashblade.EnchantmentDefinition;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 雫工作台 JEI 分类：9x9 输入网格 + 输出槽。
 */
public class ShizukuCraftingTableCategory extends AbstractRecipeCategory<ITierCraftingRecipe> {

    public static final RecipeType<ITierCraftingRecipe> RECIPE_TYPE =
            RecipeType.create("shizuku", "shizuku_crafting_table_recipe", ITierCraftingRecipe.class);

    public static final ResourceLocation GUI_TEXTURE =
            new ResourceLocation("shizuku", "textures/gui/jei/shizuku_crafting_jei.png");

    /** {@code SlashBladeIngredient#request}（私有、无 getter），用于取配方要求的刀 id。 */
    private static final Field REQUEST_FIELD = findRequestField();

    private static Field findRequestField() {
        // 按「字段类型」找，而不是按名字找 —— 万一上游改了字段名也不受影响
        Class<?> type = SlashBladeIngredient.class;
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (RequestDefinition.class.isAssignableFrom(field.getType())) {
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
                                new ResourceLocation("shizuku", "shizuku_crafting_table")))),
                GUI_WIDTH, GUI_HEIGHT);
        this.background = guiHelper.createDrawable(GUI_TEXTURE, 0, 0, GUI_WIDTH, GUI_HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        new ResourceLocation("shizuku", "shizuku_crafting_table"))));
    }

    @Override
    public @NotNull IDrawable getBackground() {
        return this.background;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull ITierCraftingRecipe recipe, @NotNull IFocusGroup focuses) {
        List<Ingredient> inputs = recipe.getIngredients();
        RegistryAccess access = Minecraft.getInstance().level.registryAccess();

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
                .addItemStack(resolveOutput(recipe, access));
    }

    /**
     * 解析配方产物。
     * 只调 {@code getResultItem()} 在 JEI 里会拿到未加载命名刀的占位栈（显示为"无铭"、伤害数值溢出），
     * 所以带 {@code blade} 字段的配方直接按 id 从命名刀注册表取完整刀。
     */
    private static ItemStack resolveOutput(ITierCraftingRecipe recipe, RegistryAccess access) {
        if (recipe instanceof ShapedTableCraftingRecipe shaped && shaped.getOutputBlade() != null) {
            SlashBladeDefinition def = access.registryOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                    .get(shaped.getOutputBlade());
            if (def != null) {
                return def.getBlade();
            }
        }
        return recipe.getResultItem(access);
    }

    /**
    /**
     * 修复 JEI 中命名刀显示为"无名无铭"样式的问题：
     * 拔刀剑配料（slashblade:blade）的 getItems() 只带名字与属性、不带模型/材质，
     * 这里按「request 的刀 id / translationKey」匹配 SlashBladeDefinition，取完整命名刀用于展示。
     */
    private static void addIngredientSlot(IRecipeLayoutBuilder builder, Ingredient ingredient,
                                          int x, int y, RegistryAccess access) {
        var slot = builder.addSlot(RecipeIngredientRole.INPUT, x, y);
        RequestDefinition request = requestedDefinition(ingredient);
        ItemStack display = resolveBladeFor(request, access);
        // 解析不出来就原样交给 JEI —— 绝不能"随便挑一把"，
        // 那样所有输入槽会显示成同一把刀（之前就是这样把全部输入变成了同一把）。
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
     * 按配方要求取真实的命名刀用于展示。
     *
     * <p>直接按定义注册表键（= 定义 JSON 文件名）查。约定：命名刀的定义文件名必须与
     * {@code name} 字段一致；不一致时按 id 查不到，图标会退化成默认模型。
     * （历史遗留的稻光/有名已于本次统一改名修正，不再做二级兜底查找。）</p>
     */
    private static ItemStack resolveBladeFor(RequestDefinition request, RegistryAccess access) {
        if (request == null || request.getName() == null) {
            return ItemStack.EMPTY;
        }
        SlashBladeDefinition definition =
                access.registryOrThrow(SlashBladeDefinition.REGISTRY_KEY).get(request.getName());
        return definition == null ? ItemStack.EMPTY : definition.getBlade().copy();
    }

    private static void addRequirementTooltip(mezz.jei.api.gui.builder.IRecipeSlotBuilder slot,
                                              RequestDefinition request) {
        List<Component> lines = new ArrayList<>();
        if (request.getKillCount() > 0) {
            lines.add(Component.translatable("jei.shizuku.requirement.kill", request.getKillCount()));
        }
        if (request.getProudSoulCount() > 0) {
            lines.add(Component.translatable("jei.shizuku.requirement.proud_soul", request.getProudSoulCount()));
        }
        if (request.getRefineCount() > 0) {
            lines.add(Component.translatable("jei.shizuku.requirement.refine", request.getRefineCount()));
        }
        for (EnchantmentDefinition enchantment : request.getEnchantments()) {
            lines.add(Component.translatable("jei.shizuku.requirement.enchant",
                    enchantment.getEnchantmentID().toString(), enchantment.getEnchantmentLevel()));
        }
        if (lines.isEmpty()) {
            return;
        }
        slot.addRichTooltipCallback((view, tooltip) -> tooltip.addAll(lines));
    }

    /** 取配料的 {@code RequestDefinition}（{@code SlashBladeIngredient.request} 私有、无 getter）。 */
    private static RequestDefinition requestedDefinition(Ingredient ingredient) {
        Object custom = ingredient.getCustomIngredient();
        if (custom == null || REQUEST_FIELD == null) {
            return null;
        }
        try {
            Object request = REQUEST_FIELD.get(custom);
            return request instanceof RequestDefinition definition ? definition : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public void draw(ITierCraftingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        if (recipe instanceof ShapedTableCraftingRecipe) {
            // 形状提示：标题旁显示图案尺寸
        }
    }
}
