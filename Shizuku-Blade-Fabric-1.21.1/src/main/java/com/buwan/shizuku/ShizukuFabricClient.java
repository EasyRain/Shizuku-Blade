package com.buwan.shizuku;

import com.buwan.shizuku.client.FieldActivateInput;
import com.buwan.shizuku.client.GalaxyFieldRenderer;
import com.buwan.shizuku.client.SynthwaveShaderRenderer;
import com.buwan.shizuku.client.TimeStopRenderer;
import com.buwan.shizuku.client.TimeStopShaderManager;
import com.buwan.shizuku.client.VoidPrisonRenderer;
import com.buwan.shizuku.client.YmsaChargeHandler;
import com.buwan.shizuku.client.YmsaClientReceiver;
import com.buwan.shizuku.client.screen.ShizukuCraftScreen;
import com.buwan.shizuku.compat.ClientTickEvent;
import com.buwan.shizuku.compat.RenderGuiEvent;
import com.buwan.shizuku.compat.RenderLevelStageEvent;
import com.buwan.shizuku.network.GalaxyFieldPacket;
import com.buwan.shizuku.network.TimeStopPacket;
import com.buwan.shizuku.network.TimeStopShaderPacket;
import com.buwan.shizuku.network.VoidPrisonPacket;
import com.buwan.shizuku.network.YmsaForceRemovePacket;
import com.buwan.shizuku.registry.ModMenus;
import com.buwan.shizuku.se.WorldsFavoriteClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
            YmsaChargeHandler.onClientTick(event);
            FieldActivateInput.tick(client);
        });

        // S2C：抹除广播
        ClientPlayNetworking.registerGlobalReceiver(YmsaForceRemovePacket.TYPE, (payload, context) ->
                context.client().execute(() -> YmsaClientReceiver.handle(payload)));

        // S2C：星河领域
        ClientPlayNetworking.registerGlobalReceiver(GalaxyFieldPacket.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.remove()) {
                        GalaxyFieldRenderer.removeZone(payload.playerId());
                    } else {
                        GalaxyFieldRenderer.addZone(payload.playerId(), payload.center(),
                                payload.radius(), payload.duration());
                    }
                }));

        // S2C：时间停止
        ClientPlayNetworking.registerGlobalReceiver(TimeStopPacket.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.remove()) {
                        TimeStopRenderer.removeTimeStopZone(payload.playerId());
                    } else {
                        TimeStopRenderer.addTimeStopZone(payload.playerId(), payload.center(),
                                payload.radius(), payload.duration());
                    }
                }));

        // S2C：虚空囚笼
        ClientPlayNetworking.registerGlobalReceiver(VoidPrisonPacket.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.remove()) {
                        VoidPrisonRenderer.removePrisonZone(payload.playerId());
                    } else {
                        VoidPrisonRenderer.addPrisonZone(payload.playerId(), payload.center(),
                                payload.radius(), payload.duration());
                    }
                }));

        // S2C：时停着色器
        ClientPlayNetworking.registerGlobalReceiver(TimeStopShaderPacket.TYPE, (payload, context) ->
                context.client().execute(() ->
                        TimeStopShaderManager.syncFromServer(payload.activate(), payload.strength())));

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

        // 合成波全屏后处理：对应 NeoForge 的 AFTER_CUTOUT_BLOCKS，Fabric 用 AFTER_ENTITIES 近似
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (context.matrixStack() == null) {
                return;
            }
            SynthwaveShaderRenderer.onRenderWorld(new RenderLevelStageEvent(
                    context.matrixStack(), context.camera(),
                    RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS, context.projectionMatrix()));
        });

        HudRenderCallback.EVENT.register((guiGraphics, tickCounter) ->
                GalaxyFieldRenderer.onRenderGuiPost(new RenderGuiEvent.Post()));
    }
}
