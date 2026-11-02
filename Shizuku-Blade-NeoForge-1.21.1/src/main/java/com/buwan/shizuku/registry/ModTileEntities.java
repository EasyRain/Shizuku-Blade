package com.buwan.shizuku.registry;

import com.buwan.shizuku.tile.ShizukuCraftTile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModTileEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "shizuku");

    public static final Supplier<BlockEntityType<ShizukuCraftTile>> SHIZUKU_CRAFT_TILE = BLOCK_ENTITIES.register(
            "shizuku_craft_tile",
            () -> BlockEntityType.Builder.of(ShizukuCraftTile::new, ModBlocks.SHIZUKU_CRAFTING_TABLE.get()).build(null)
    );
}
