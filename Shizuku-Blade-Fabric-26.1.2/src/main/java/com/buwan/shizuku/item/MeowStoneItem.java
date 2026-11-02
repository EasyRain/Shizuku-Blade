package com.buwan.shizuku.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class MeowStoneItem extends Item {

    public MeowStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        // 原版 3.4.0 误用 literal 导致显示原始键，改为 translatable 正确读取语言文件
        tooltip.accept(Component.translatable("item.shizuku.meow_stone.tooltip"));
        tooltip.accept(Component.translatable("item.shizuku.meow_stone.tooltip2"));
        tooltip.accept(Component.translatable("item.shizuku.meow_stone.tooltip3"));
    }
}
