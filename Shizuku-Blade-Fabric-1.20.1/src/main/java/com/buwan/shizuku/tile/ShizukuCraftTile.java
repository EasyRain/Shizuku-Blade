package com.buwan.shizuku.tile;

import com.buwan.shizuku.compat.ItemStackHandler;
import com.buwan.shizuku.menu.ShizukuCraftMenu;
import com.buwan.shizuku.registry.ModTileEntities;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ShizukuCraftTile extends BlockEntity implements MenuProvider, ExtendedScreenHandlerFactory {

    public static final int GRID = 9;
    public static final int SLOT_COUNT = 81;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT);

    public ShizukuCraftTile(BlockPos pos, BlockState blockState) {
        super(ModTileEntities.SHIZUKU_CRAFT_TILE.get(), pos, blockState);
    }

    public ItemStackHandler getInventory() {
        return this.inventory;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.shizuku.shizuku_crafting_table");
    }

    @Override
    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
        buf.writeBlockPos(this.getBlockPos());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ShizukuCraftMenu(containerId, playerInventory, this.getBlockPos());
    }

    @Override
    protected void load(CompoundTag tag) {
        super.load(tag);
        this.inventory.deserializeNBT(tag.getCompound("Inventory"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", this.inventory.serializeNBT());
    }
}
