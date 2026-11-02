package com.buwan.shizuku;

import com.buwan.shizuku.client.screen.ShizukuCraftScreen;
import com.buwan.shizuku.registry.ModMenus;
import com.buwan.shizuku.se.WorldsFavoriteClient;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

/** Fabric 客户端入口：注册菜单界面与客户端专用逻辑。 */
public class ShizukuFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenus.SHIZUKU_CRAFT_TABLE.get(), ShizukuCraftScreen::new);
        WorldsFavoriteClient.register();
    }
}
