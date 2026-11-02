package com.buwan.shizuku.client.screen;

import com.buwan.shizuku.menu.ShizukuCraftMenu;
import com.buwan.shizuku.shizuku;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * 雫工作台界面（26.1.2：Screen 改为「提取渲染状态」模型，
 * 渲染入口是 extractRenderState/extractContents/extractLabels）。
 */
public class ShizukuCraftScreen extends AbstractContainerScreen<ShizukuCraftMenu> {

    private static final Identifier BACKGROUND =
            shizuku.prefix("textures/gui/shizuku_crafting_table_gui.png");

    public ShizukuCraftScreen(ShizukuCraftMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 234, 278);
    }

    @Override
    protected void init() {
        super.init();
        FallingPetalRenderer.init(this.width, this.height);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        FallingPetalRenderer.tick(this.width, this.height);
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(BACKGROUND, this.leftPos, this.topPos,
                this.imageWidth, this.imageHeight, 0.0F, 0.0F, 512.0F, 512.0F);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        FallingPetalRenderer.render(graphics, this.width, this.height, partialTick);
    }

    @Override
    protected void extractLabels(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, 8, 6, 4210752, false);
        graphics.text(this.font, this.playerInventoryTitle, 39, this.imageHeight - 94, 4210752, false);
    }
}
