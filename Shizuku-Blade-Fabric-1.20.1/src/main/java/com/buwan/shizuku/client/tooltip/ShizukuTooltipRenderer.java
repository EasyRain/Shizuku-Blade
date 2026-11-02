package com.buwan.shizuku.client.tooltip;

import com.buwan.shizuku.util.tooltip.ColorHelper;
import com.buwan.shizuku.util.tooltip.GradientConfig;
import com.buwan.shizuku.util.tooltip.ICustomTooltip;
import com.buwan.shizuku.util.tooltip.TooltipFrameRenderer;
import com.buwan.shizuku.util.tooltip.RainbowTextUtil;
import com.buwan.shizuku.util.tooltip.RoundedRectRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;




import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4i;

import java.util.ArrayList;
import java.util.List;


public class ShizukuTooltipRenderer {
    private static final ResourceLocation SHIZUKU_BACKGROUND = new ResourceLocation("shizuku", "textures/gui/shizuku_blade_bg.png");
    private static final int SEPARATOR_COLOR = -2130739136;

    
    /** 无铭「有名」刀专属 tooltip（由 mixin 在 renderTooltip 时调用）；返回 true 表示已自行绘制。 */
    public static boolean renderYmbladeTooltip(GuiGraphics graphics, ItemStack stack, Font font, int x, int y) {
        if (!isShizukuNamedBlade(stack)) {
            return false;
        }
        List<Component> textLines = generateTooltipText(stack, font);
        renderCustomTooltip(graphics, textLines, x, y, font);
        return true;
    }

    private static boolean isShizukuNamedBlade(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return CapabilitySlashBlade.BLADESTATE.maybeGet(stack)
                .map(s -> "item.shizuku.ymblade".equals(s.getTranslationKey()))
                .orElse(false);
    }

    private static List<Component> generateTooltipText(ItemStack stack, Font font) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(stack.getHoverName());
        List<Component> itemTooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, null, itemTooltip, TooltipFlag.NORMAL);
        tooltip.addAll(itemTooltip);
        tooltip.add(Component.empty());
        tooltip.add(Component.empty());
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
        tooltip.add(Component.empty());
        tooltip.add(Component.empty());
        return tooltip;
    }

    public static void renderCustomTooltip(GuiGraphics graphics, List<Component> components, int x, int y, Font font) {
        renderCustomTooltip(graphics, components, x, y, font, ICustomTooltip.TooltipType.DEFAULT);
    }

    public static void renderCustomTooltip(GuiGraphics graphics, List<Component> components, int x, int y, Font font, ICustomTooltip.TooltipType tooltipType) {
        if (!components.isEmpty()) {
            // 3.6.0：宇宙刀（COSMIC）特殊信息框与 1.20.1 原版一致：
            // 全透明背景 + 纯白边框 + ShizukuBlade 流动渐变文字（白色带黄边霓虹位移特效）
            boolean cosmic = tooltipType == ICustomTooltip.TooltipType.COSMIC;
            List<Integer> palette = ColorHelper.ShizukuBlade;
            List<Integer> lastLineColors = ColorHelper.ShizukuBlade_B;
            PoseStack poseStack = graphics.pose();
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 400.0F);
            List<TooltipLine> lines = parseTooltipLines(components, font, palette, lastLineColors);
            int maxWidth = 0;

            for (TooltipLine line : lines) {
                maxWidth = Math.max(maxWidth, line.width + 40);
            }

            int tooltipHeight = 16;

            for (TooltipLine tooltipLine : lines) {
                if (tooltipLine.isSeparator) {
                    tooltipHeight += 6;
                } else {
                    tooltipHeight += 10;
                }
            }

            int screenWidth = graphics.guiWidth();
            int screenHeight = graphics.guiHeight();
            if (x + maxWidth > screenWidth) {
                x = screenWidth - maxWidth - 10;
            }

            if (y + tooltipHeight > screenHeight) {
                y = screenHeight - tooltipHeight - 10;
            }

            if (!cosmic) {
                // 图层顺序（与原版一致）：立绘（最底，可溢出）→ 背景+边框（几乎不透明，盖住立绘）→ 文字
                renderShizukuBackground(graphics, x - 80, y - 140, 300, 320);
                renderTooltipBackground(graphics, x, y, maxWidth, tooltipHeight, palette);
            } else {
                renderCosmicTooltipBackground(graphics, x, y, maxWidth, tooltipHeight, palette);
            }

            int currentY = y + 8;

            for (TooltipLine tooltipLine : lines) {
                if (tooltipLine.isSeparator) {
                    renderSeparatorLine(graphics, x + 8, currentY, maxWidth - 16);
                    currentY += 6;
                } else {
                    int textX = tooltipLine.isTitle ? x + (maxWidth - tooltipLine.width) / 2 : x + 12;
                    renderRainbowText(graphics, tooltipLine.component, textX, currentY, font);
                    currentY += 10;
                }
            }

            poseStack.popPose();
        }
    }

    private static void renderShizukuBackground(GuiGraphics graphics, int x, int y, int width, int height) {
        RenderSystem.setShaderTexture(0, SHIZUKU_BACKGROUND);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        long time = System.currentTimeMillis();
        float baseAngle = (float) (time % 6000L) / 6000.0F * 360.0F;
        float angle2 = baseAngle + 120.0F;
        float angle3 = baseAngle + 240.0F;
        float orbitRadius = 20.0F;
        float offset1X = (float) Math.cos(Math.toRadians(baseAngle)) * orbitRadius;
        float offset1Y = (float) Math.sin(Math.toRadians(baseAngle)) * orbitRadius;
        float offset2X = (float) Math.cos(Math.toRadians(angle2)) * orbitRadius;
        float offset2Y = (float) Math.sin(Math.toRadians(angle2)) * orbitRadius;
        float offset3X = (float) Math.cos(Math.toRadians(angle3)) * orbitRadius;
        float offset3Y = (float) Math.sin(Math.toRadians(angle3)) * orbitRadius;
        PoseStack poseStack = graphics.pose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.3F);
        poseStack.pushPose();
        poseStack.translate(offset1X, offset1Y, 0.0F);
        graphics.blit(SHIZUKU_BACKGROUND, x, y, 0.0F, 0.0F, width, height, width, height);
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.translate(offset2X, offset2Y, 0.0F);
        graphics.blit(SHIZUKU_BACKGROUND, x, y, 0.0F, 0.0F, width, height, width, height);
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.translate(offset3X, offset3Y, 0.0F);
        graphics.blit(SHIZUKU_BACKGROUND, x, y, 0.0F, 0.0F, width, height, width, height);
        poseStack.popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(SHIZUKU_BACKGROUND, x, y, 0.0F, 0.0F, width, height, width, height);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static void renderTooltipBackground(GuiGraphics graphics, int x, int y, int width, int height, List<Integer> borderColors) {
        // SDF 着色器画圆角面板：圆角在任何 GUI 缩放下都是平滑的（不受 GUI 像素网格限制）
        TooltipFrameRenderer.draw(graphics, x, y, width, height, 12.0F, 2.0F, 0xDC87CEFA, 0xFFFFFFFF);
    }

    // 3.6.0：宇宙刀 tooltip —— 与 1.20.1 原版一致：背景全透明（不画底板），仅纯白边框
    private static void renderCosmicTooltipBackground(GuiGraphics graphics, int x, int y, int width, int height, List<Integer> borderColors) {
        TooltipFrameRenderer.draw(graphics, x, y, width, height, 12.0F, 2.0F, 0x0087CEFA, 0xFFFFFFFF);
    }

    private static void renderSeparatorLine(GuiGraphics graphics, int x, int y, int width) {
        PoseStack poseStack = graphics.pose();
        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = com.buwan.shizuku.compat.TesselatorCompat.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int centerX = x + width / 2;
        addGradientVertex(buffer, matrix, (float) x, (float) y, 16744512);
        addGradientVertex(buffer, matrix, (float) centerX, (float) y, SEPARATOR_COLOR);
        addGradientVertex(buffer, matrix, (float) centerX, (float) (y + 2), SEPARATOR_COLOR);
        addGradientVertex(buffer, matrix, (float) x, (float) (y + 2), 16744512);
        addGradientVertex(buffer, matrix, (float) centerX, (float) y, SEPARATOR_COLOR);
        addGradientVertex(buffer, matrix, (float) (x + width), (float) y, 16744512);
        addGradientVertex(buffer, matrix, (float) (x + width), (float) (y + 2), 16744512);
        addGradientVertex(buffer, matrix, (float) centerX, (float) (y + 2), SEPARATOR_COLOR);
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    private static void renderRainbowText(GuiGraphics graphics, Component component, int x, int y, Font font) {
        graphics.drawString(font, component, x, y, -1642241, false);
    }

    private static void addGradientVertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, int color) {
        int a = color >> 24 & 0xFF;
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        buffer.vertex(matrix, x, y, 0.0F).color(r, g, b, a).endVertex();
    }

    private static List<TooltipLine> parseTooltipLines(List<Component> components, Font font, List<Integer> palette, List<Integer> lastLineColors) {
        List<TooltipLine> lines = new ArrayList<>();
        RainbowTextUtil.RainbowConfig nameConfig = new RainbowTextUtil.RainbowConfig()
                .setSpeed(10.0F)
                .setAnimationType(RainbowTextUtil.AnimationType.FLOWING)
                .setColorSpan(0.1F)
                .setColors(palette);
        RainbowTextUtil.RainbowConfig descConfig = new RainbowTextUtil.RainbowConfig()
                .setSpeed(17.0F)
                .setAnimationType(RainbowTextUtil.AnimationType.FLOWING)
                .setColorSpan(0.03F)
                .setColors(palette);
        RainbowTextUtil.RainbowConfig lastLineConfig = new RainbowTextUtil.RainbowConfig()
                .setSpeed(17.0F)
                .setAnimationType(RainbowTextUtil.AnimationType.FLOWING)
                .setColorSpan(0.03F)
                .setColors(lastLineColors);
        boolean isFirstLine = true;
        boolean separatorAdded = false;
        int totalLines = components.size();

        for (int i = 0; i < totalLines; i++) {
            Component component = components.get(i);
            String text = component.getString().replaceAll("§.", "");
            if (isFirstLine) {
                Component rainbow = RainbowTextUtil.createRainbowText(text, nameConfig);
                lines.add(new TooltipLine(rainbow, text, font.width(text), true, false));
                isFirstLine = false;
            } else {
                if (!separatorAdded) {
                    lines.add(new TooltipLine(Component.empty(), "", 0, false, true));
                    separatorAdded = true;
                }

                boolean isLastLine = i == totalLines - 1;
                String[] subLines = text.split("\\n");

                for (int j = 0; j < subLines.length; j++) {
                    String subLine = subLines[j].replaceAll("§.", "");
                    if (subLine.isEmpty()) {
                        lines.add(new TooltipLine(Component.empty(), "", 0, false, false));
                    } else if (!subLine.trim().isEmpty()) {
                        boolean isLastSubLine = isLastLine && j == subLines.length - 1;
                        RainbowTextUtil.RainbowConfig config = isLastSubLine ? lastLineConfig : descConfig;
                        Component rainbow = RainbowTextUtil.createRainbowText(subLine, config);
                        lines.add(new TooltipLine(rainbow, subLine, font.width(subLine), false, false));
                    }
                }
            }
        }

        return lines;
    }

    private static class TooltipLine {
        Component component;
        String text;
        int width;
        boolean isTitle;
        boolean isSeparator;

        TooltipLine(Component component, String text, int width, boolean isTitle, boolean isSeparator) {
            this.component = component;
            this.text = text;
            this.width = width;
            this.isTitle = isTitle;
            this.isSeparator = isSeparator;
        }
    }
}
