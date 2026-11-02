package com.buwan.shizuku.registry;

import com.buwan.shizuku.client.screen.ShizukuCraftScreen;
import com.buwan.shizuku.menu.ShizukuCraftMenu;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "shizuku");

    public static final Supplier<MenuType<ShizukuCraftMenu>> SHIZUKU_CRAFT_TABLE = MENUS.register(
            "shizuku_craft_table",
            () -> IMenuTypeExtension.create(ShizukuCraftMenu::new)
    );

    @OnlyIn(Dist.CLIENT)
    public static void onClientSetup(RegisterMenuScreensEvent event) {
        event.register(SHIZUKU_CRAFT_TABLE.get(), ShizukuCraftScreen::new);
    }
}
