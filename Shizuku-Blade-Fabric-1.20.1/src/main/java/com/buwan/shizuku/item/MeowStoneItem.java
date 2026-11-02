package com.buwan.shizuku.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class MeowStoneItem extends Item {

    public MeowStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        // 原版 3.4.0 误用 literal 导致显示原始键，改为 translatable 正确读取语言文件
        tooltip.add(Component.translatable("item.shizuku.meow_stone.tooltip"));
        tooltip.add(Component.translatable("item.shizuku.meow_stone.tooltip2"));
        tooltip.add(Component.translatable("item.shizuku.meow_stone.tooltip3"));
    }
}
