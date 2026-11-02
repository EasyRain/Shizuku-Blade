package com.buwan.shizuku.registry;

import com.buwan.shizuku.compat.DeferredHolder;
import com.buwan.shizuku.compat.DeferredRegister;
import com.buwan.shizuku.menu.ShizukuCraftMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, "shizuku");

    // 1.20.1 的 ExtendedScreenHandlerType 没有 packetCodec 参数，
    // 打开数据由 ExtendedScreenHandlerFactory#writeScreenOpeningData 写入。
    public static final DeferredHolder<MenuType<?>, MenuType<ShizukuCraftMenu>> SHIZUKU_CRAFT_TABLE =
            MENUS.register("shizuku_craft_table",
                    () -> new ExtendedScreenHandlerType<>(
                            (syncId, inv, buf) -> new ShizukuCraftMenu(syncId, inv, buf.readBlockPos())));
}
