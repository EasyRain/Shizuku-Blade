package com.buwan.shizuku.registry;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import static com.buwan.shizuku.shizuku.ITEMS;

public class ModItems {
    public static final RegistryObject<Item> MEOW_STONE = ITEMS.register("meow_stone",
            () -> new Item(new Item.Properties()));
}