package com.buwan.shizuku.jei;

import com.buwan.shizuku.client.screen.ShizukuCraftScreen;
import com.buwan.shizuku.crafting.ITierCraftingRecipe;
import com.buwan.shizuku.jei.category.ShizukuCraftingTableCategory;
import com.buwan.shizuku.menu.ShizukuCraftMenu;
import com.buwan.shizuku.registry.ModBlocks;
import com.buwan.shizuku.registry.ModMenus;
import com.buwan.shizuku.registry.ModRecipeTypes;
import com.buwan.shizuku.shizuku;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class ShizukuJeiPlugin implements IModPlugin {

    public static final ResourceLocation UID = shizuku.prefix("jei_plugin");

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(mezz.jei.api.registration.ISubtypeRegistration registration) {
        // JEI 会把「同名物品」按 subtype 去重。slashblade:slashblade 的每一把刀都靠 BLADESTATE 区分，
        // 不注册 subtype interpreter 的话，所有刀会被合并成同一个条目，只显示默认的无名占位刀
        // ——这就是合成表里输入刀全是"无名"的根因（普通工作台与自定义合成台都一样）。
        registration.registerSubtypeInterpreter(mods.flammpfeil.slashblade.init.SBItems.SLASHBLADE,
                (stack, context) -> CapabilitySlashBlade.BLADESTATE.maybeGet(stack)
                        .map(state -> state.getTranslationKey())
                        .orElse(""));
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ShizukuCraftingTableCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registration) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world != null) {
            List<ITierCraftingRecipe> recipes = world.getRecipeManager()
                    .getAllRecipesFor(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get());
            registration.addRecipes(ShizukuCraftingTableCategory.RECIPE_TYPE, recipes);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.SHIZUKU_CRAFTING_TABLE.get()),
                ShizukuCraftingTableCategory.RECIPE_TYPE
        );
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                ShizukuCraftMenu.class,
                ModMenus.SHIZUKU_CRAFT_TABLE.get(),
                ShizukuCraftingTableCategory.RECIPE_TYPE,
                1, 81, 82, 36
        );
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(ShizukuCraftScreen.class, 174, 90, 22, 12,
                ShizukuCraftingTableCategory.RECIPE_TYPE);
    }
}
