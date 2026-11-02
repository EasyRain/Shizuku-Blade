package com.buwan.shizuku.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取当前悬停槽位（26.1 仍为 {@code hoveredSlot} 字段）。 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("hoveredSlot")
    Slot shizuku$getHoveredSlot();
}
