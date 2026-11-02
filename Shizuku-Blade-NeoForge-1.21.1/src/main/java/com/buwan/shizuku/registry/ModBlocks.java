package com.buwan.shizuku.registry;

import com.buwan.shizuku.block.ShizukuCraftTableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("shizuku");
    public static final DeferredBlock<Block> SHIZUKU_CRAFTING_TABLE = BLOCKS.register(
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
