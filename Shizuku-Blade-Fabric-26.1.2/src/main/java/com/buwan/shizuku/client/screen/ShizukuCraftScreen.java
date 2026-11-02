package com.buwan.shizuku.client.screen;

import com.buwan.shizuku.menu.ShizukuCraftMenu;
import com.buwan.shizuku.shizuku;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class ShizukuCraftScreen extends AbstractContainerScreen<ShizukuCraftMenu> {

    private static final Identifier BACKGROUND =
            shizuku.prefix("textures/gui/shizuku_crafting_table_gui.png");

    public ShizukuCraftScreen(ShizukuCraftMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 234;
        this.imageHeight = 278;
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
    public void render(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        FallingPetalRenderer.render(graphics, this.width, this.height, partialTick);
        // 修复：1.21.1 的 AbstractContainerScreen.render 不再自动绘制槽位悬停提示，
        // 必须显式调用 renderTooltip，否则指向物品不显示任何信息。
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 512, 512);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 8, 6, 4210752, false);
        graphics.drawString(this.font, this.playerInventoryTitle, 39, this.imageHeight - 94, 4210752, false);
    }
}
