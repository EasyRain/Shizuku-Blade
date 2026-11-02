package com.buwan.shizuku;

import com.buwan.shizuku.client.GalaxyFieldRenderer;
import com.buwan.shizuku.client.TimeStopRenderer;
import com.buwan.shizuku.client.VoidPrisonRenderer;
import com.buwan.shizuku.client.screen.ShizukuCraftScreen;
import com.buwan.shizuku.compat.ClientTickEvent;
import com.buwan.shizuku.compat.RenderGuiEvent;
import com.buwan.shizuku.compat.RenderLevelStageEvent;
import com.buwan.shizuku.registry.ModMenus;
import com.buwan.shizuku.se.WorldsFavoriteClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Fabric 客户端入口：菜单界面、坐标位移、渲染器事件派发。
 * NeoForge 的 ClientTickEvent / RenderLevelStageEvent / RenderGuiEvent
 * 在这里用 Fabric 的 ClientTickEvents / WorldRenderEvents / HudRenderCallback 派发到 compat 事件。
 */
public class ShizukuFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenus.SHIZUKU_CRAFT_TABLE.get(), ShizukuCraftScreen::new);
        WorldsFavoriteClient.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientTickEvent.Post event = new ClientTickEvent.Post();
            GalaxyFieldRenderer.onClientTick(event);
            TimeStopRenderer.getInstance().onClientTick(event);
            VoidPrisonRenderer.getInstance().onClientTick(event);
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (context.matrixStack() == null) {
                return;
            }
            RenderLevelStageEvent event = new RenderLevelStageEvent(
                    context.matrixStack(), context.camera(),
                    RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS);
            GalaxyFieldRenderer.onRenderLevel(event);
            TimeStopRenderer.getInstance().onRenderLevel(event);
            VoidPrisonRenderer.getInstance().onRenderLevel(event);
        });

        HudRenderCallback.EVENT.register((guiGraphics, tickCounter) ->
                GalaxyFieldRenderer.onRenderGuiPost(new RenderGuiEvent.Post()));
    }
}
