package com.buwan.shizuku.registry;

import com.buwan.shizuku.compat.DeferredRegister;
import com.buwan.shizuku.tile.ShizukuCraftTile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

public class ModTileEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, "shizuku");

    public static final Supplier<BlockEntityType<ShizukuCraftTile>> SHIZUKU_CRAFT_TILE = BLOCK_ENTITIES.register(
            "shizuku_craft_tile",
            // 26.1 移除了 BlockEntityType.Builder，构造器是 private（由 shizuku.accesswidener 开放）
            () -> new BlockEntityType<>(ShizukuCraftTile::new,
                    java.util.Set.of(ModBlocks.SHIZUKU_CRAFTING_TABLE.get()))
    );
}
