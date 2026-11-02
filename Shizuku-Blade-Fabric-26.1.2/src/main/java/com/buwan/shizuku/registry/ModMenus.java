package com.buwan.shizuku.registry;

import com.buwan.shizuku.compat.DeferredHolder;
import com.buwan.shizuku.compat.DeferredRegister;
import com.buwan.shizuku.menu.ShizukuCraftMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.inventory.MenuType;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, "shizuku");

    public static final StreamCodec<RegistryFriendlyByteBuf, BlockPos> POS_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, BlockPos pos) -> buf.writeBlockPos(pos),
            (RegistryFriendlyByteBuf buf) -> buf.readBlockPos());

    public static final DeferredHolder<MenuType<?>, MenuType<ShizukuCraftMenu>> SHIZUKU_CRAFT_TABLE =
            MENUS.register("shizuku_craft_table",
                    () -> new ExtendedMenuType<>(
                            (syncId, inv, pos) -> new ShizukuCraftMenu(syncId, inv, pos),
                            POS_CODEC));
}
