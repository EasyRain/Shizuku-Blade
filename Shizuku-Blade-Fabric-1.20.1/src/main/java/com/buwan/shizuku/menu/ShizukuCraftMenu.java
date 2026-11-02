package com.buwan.shizuku.menu;

import com.buwan.shizuku.compat.ItemStackHandler;
import com.buwan.shizuku.container.ModCraftContainer;
import com.buwan.shizuku.container.slot.ModCraftResultSlot;
import com.buwan.shizuku.crafting.ITierCraftingRecipe;
import com.buwan.shizuku.registry.ModMenus;
import com.buwan.shizuku.registry.ModRecipeTypes;
import com.buwan.shizuku.tile.ShizukuCraftTile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class ShizukuCraftMenu extends AbstractContainerMenu {

    public static final int GRID = 9;
    public static final int MAIN_X = 8;
    public static final int MAIN_Y = 18;
    public static final int PLAYER_INV_X = 39;
    public static final int PLAYER_INV_Y = 196;
    public static final int HOTBAR_Y = 254;
    public static final int OUT_X = 206;
    public static final int OUT_Y = 89;

    private final Level world;
    private final Container result;
    private final Player player;
    private final BlockPos blockPos;
    public final ModCraftContainer matrix;

    public ShizukuCraftMenu(int id, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.SHIZUKU_CRAFT_TABLE.get(), id);
        this.player = playerInventory.player;
        this.world = playerInventory.player.level();
        this.blockPos = pos;
        ItemStackHandler handler;
        if (this.world.getBlockEntity(pos) instanceof ShizukuCraftTile tile) {
            handler = tile.getInventory();
        } else {
            handler = new ItemStackHandler(ShizukuCraftTile.SLOT_COUNT);
        }

        this.result = new ResultContainer();
        this.matrix = new ModCraftContainer(this, handler, ShizukuCraftTile.SLOT_COUNT);
        this.addSlot(new ModCraftResultSlot(this.player, this, this.matrix, this.result, 81, OUT_X, OUT_Y));

        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                this.addSlot(new Slot(this.matrix, j + i * GRID, MAIN_X + j * 18, MAIN_Y + i * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(playerInventory, j + row * 9 + 9, PLAYER_INV_X + j * 18, PLAYER_INV_Y + row * 18));
            }
        }

        for (int j = 0; j < 9; j++) {
            this.addSlot(new Slot(playerInventory, j, PLAYER_INV_X + j * 18, HOTBAR_Y));
        }

        this.slotsChanged(this.matrix);
    }

    @Override
    public void slotsChanged(@NotNull Container matrix) {
        if (!this.world.isClientSide()) {
            Optional<ITierCraftingRecipe> recipe = this.world.getRecipeManager()
                    .getRecipeFor(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get(), this.matrix, this.world);
            if (recipe.isPresent() && !matrix.isEmpty()) {
                ItemStack resultStack = recipe.get().assemble(this.matrix, this.world.registryAccess());
                this.result.setItem(0, resultStack);
            } else {
                this.result.setItem(0, ItemStack.EMPTY);
            }
            super.slotsChanged(matrix);
        }
    }

    @Override
    @NotNull
    public ItemStack quickMoveStack(@NotNull Player player, int slotNumber) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotNumber);
        int inSlots = 82;
        int invSlots = 118;
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (slotNumber == 0) {
                if (!this.moveItemStackTo(itemstack1, inSlots, invSlots, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(itemstack1, itemstack);
            } else if (slotNumber >= inSlots && slotNumber < invSlots) {
                if (!this.moveItemStackTo(itemstack1, 1, inSlots, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, inSlots, invSlots, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.distanceToSqr((double) this.blockPos.getX() + 0.5,
                (double) this.blockPos.getY() + 0.5,
                (double) this.blockPos.getZ() + 0.5) <= 64.0;
    }
}
