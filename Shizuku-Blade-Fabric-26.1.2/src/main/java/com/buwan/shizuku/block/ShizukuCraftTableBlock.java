package com.buwan.shizuku.block;

import com.buwan.shizuku.compat.ItemStackHandler;
import com.buwan.shizuku.tile.ShizukuCraftTile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ShizukuCraftTableBlock extends Block implements EntityBlock {

    public ShizukuCraftTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ShizukuCraftTile table) {
            serverPlayer.openMenu(table);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void destroy(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof ShizukuCraftTile table) {
            ItemStackHandler handler = table.getInventory();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty() && level instanceof Level realLevel) {
                    Containers.dropItemStack(realLevel, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }
            if (level instanceof Level realLevel) {
                realLevel.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.destroy(level, pos, state);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShizukuCraftTile(pos, state);
    }
}
