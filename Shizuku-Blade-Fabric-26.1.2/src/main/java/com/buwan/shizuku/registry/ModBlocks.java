package com.buwan.shizuku.registry;

import com.buwan.shizuku.block.ShizukuCraftTableBlock;
import com.buwan.shizuku.compat.DeferredHolder;
import com.buwan.shizuku.compat.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, "shizuku");

    public static final DeferredHolder<Block, Block> SHIZUKU_CRAFTING_TABLE = BLOCKS.register(
            "shizuku_crafting_table",
            () -> new ShizukuCraftTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .sound(SoundType.WOOD)
                    .destroyTime(2.5F)
                    .lightLevel(state -> 15))
    );

    public static Supplier<Block> craftTable() {
        return SHIZUKU_CRAFTING_TABLE;
    }
}
