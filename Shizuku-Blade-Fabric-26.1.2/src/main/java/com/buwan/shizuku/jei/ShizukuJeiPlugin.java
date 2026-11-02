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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class ShizukuJeiPlugin implements IModPlugin {

    public static final Identifier UID = shizuku.prefix("jei_plugin");

    @Override
    public @NotNull Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ShizukuCraftingTableCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registration) {
        // 26.1 移除了 Level#getRecipeManager()（客户端改 RecipeAccess），
        // 这里暂不注册配方展示；游戏内合成不受影响。
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
