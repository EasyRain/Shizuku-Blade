package com.buwan.shizuku.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取当前悬停槽位（{@code @Shadow} 在该字段上未能生成 refmap 映射，改用 @Accessor）。 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("hoveredSlot")
    Slot shizuku$getHoveredSlot();
}
