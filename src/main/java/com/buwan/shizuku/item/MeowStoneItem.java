package com.buwan.shizuku.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 喵石：纯粹的材料物品，只额外追加一行 tooltip。
 */
public class MeowStoneItem extends Item {
    public MeowStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.shizuku.meow_stone.tooltip"));
        tooltip.add(Component.translatable("item.shizuku.meow_stone.tooltip2"));
        tooltip.add(Component.translatable("item.shizuku.meow_stone.tooltip3",
            com.buwan.shizuku.sa.JudgmentConfig.meowStoneDropPercent()));
    }
}
