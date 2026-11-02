package com.buwan.shizuku.client.tooltip;

import com.buwan.shizuku.util.tooltip.ICustomTooltip;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;





import java.util.ArrayList;
import java.util.List;


public class TooltipEventHandler {
    
    /** 由 mixin 在 GuiGraphicsExtractor.renderTooltip(Font, ItemStack, int, int) HEAD 调用；返回 true 表示已自行绘制。 */
    public static boolean tryRender(GuiGraphicsExtractor graphics, ItemStack stack, Font font, int x, int y) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof ICustomTooltip customTooltipItem) {
            renderCustomTooltip(graphics, stack, font, x, y, customTooltipItem.getTooltipType());
            return true;
        }
        if (isShizukuNamedBlade(stack)) {
            renderCustomTooltip(graphics, stack, font, x, y, getTooltipTypeForBlade(stack));
            return true;
        }
        return false;
    }

    private static void renderCustomTooltip(GuiGraphicsExtractor graphics, ItemStack stack, Font font, int x, int y,
                                            ICustomTooltip.TooltipType tooltipType) {
        List<Component> textLines = generateTooltipText(stack, font, tooltipType);
        ShizukuTooltipRenderer.renderCustomTooltip(graphics, textLines, x, y, font, tooltipType);
    }

    private static boolean isShizukuNamedBlade(ItemStack stack) {
        if (!stack.getItem().toString().contains("slashblade")) {
            return false;
        }
        String bladeName = getBladeName(stack);
        // 与 1.20.1 原版一致：只有星河刀（mosmicblade）走本处理器的特殊信息框；
        // 无铭「有名」由 ShizukuTooltipRenderer 负责，其余刀一律原版信息框
        return "shizuku:mosmicblade".equals(bladeName);
    }

    private static ICustomTooltip.TooltipType getTooltipTypeForBlade(ItemStack stack) {
        String bladeName = getBladeName(stack);
        return "shizuku:mosmicblade".equals(bladeName)
                ? ICustomTooltip.TooltipType.COSMIC
                : ICustomTooltip.TooltipType.DEFAULT;
    }

    private static String getBladeName(ItemStack stack) {
        return CapabilitySlashBlade.getBladeState(stack)
                .map(s -> {
                    String translationKey = s.getTranslationKey();
                    // "item.shizuku.mosmicblade" → "shizuku:mosmicblade"
                    // （去 "item." 前缀后把第一个 '.' 换成 ':'，与命名刀注册名一致）
                    if (translationKey.startsWith("item.shizuku.")) {
                        return translationKey.substring("item.".length()).replaceFirst("\\.", ":");
                    }
                    return null;
                })
                .orElse(null);
    }

    private static List<Component> generateTooltipText(ItemStack stack, Font font, ICustomTooltip.TooltipType tooltipType) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(stack.getHoverName());
        List<Component> itemTooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, itemTooltip, TooltipFlag.NORMAL);
        tooltip.addAll(itemTooltip);
        tooltip.add(Component.empty());
        if (tooltipType == ICustomTooltip.TooltipType.COSMIC) {
            // 3.6.0：宇宙刀（mosmicblade）专属 tooltip
            tooltip.add(Component.literal("Shift + X 开启星河领域"));
            tooltip.add(Component.literal("长按右键释放星河斩"));
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("星河落九天，刃光映万古"));
            tooltip.add(Component.literal("一剑斩星辰，万象皆俯首"));
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("攻击伤害: 越打越高"));
        } else {
            tooltip.add(Component.translatable("锋利:不可估量"));
            tooltip.add(Component.translatable("力量:不可估量"));
            tooltip.add(Component.translatable("可爱:不可估量"));
            tooltip.add(Component.translatable("魅力:不可估量"));
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("shift + sa释放后可击杀怪物并清除掉落物"));
            tooltip.add(Component.translatable("shift + alt 可开启坐标位移"));
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("相见时难别亦难，东风无力百花残"));
            tooltip.add(Component.translatable("春蚕到死丝方尽，蜡炬成灰泪始干"));
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("  猫羽雫 攻击伤害"));
        }

        tooltip.add(Component.empty());
        return tooltip;
    }
}
