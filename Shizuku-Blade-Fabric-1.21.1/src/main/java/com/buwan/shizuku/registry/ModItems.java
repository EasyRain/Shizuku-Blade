package com.buwan.shizuku.registry;

import com.buwan.shizuku.compat.DeferredHolder;
import com.buwan.shizuku.item.MeowStoneItem;
import net.minecraft.world.item.Item;

import static com.buwan.shizuku.shizuku.MEOW_STONE;

public class ModItems {
    // 喵石由主类统一注册，这里仅保留引用，避免重复注册同名物品
    public static final DeferredHolder<Item, Item> MEOW_STONE_REF = MEOW_STONE;

    // 喵石物品类（带 tooltip）
    public static final Class<MeowStoneItem> MEOW_STONE_ITEM_CLASS = MeowStoneItem.class;
}
