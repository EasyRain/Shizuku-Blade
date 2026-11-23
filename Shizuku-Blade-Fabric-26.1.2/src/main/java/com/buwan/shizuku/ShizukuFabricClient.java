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
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;

/**
 * Fabric 客户端入口：菜单界面、坐标位移、渲染器事件派发。
 * NeoForge 的 ClientTickEvent / RenderLevelStageEvent / RenderGuiEvent
 * 在这里用 Fabric 的 ClientTickEvents / WorldRenderEvents / HudRenderCallback 派发到 compat 事件。
 */
public class ShizukuFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 键位必须在 Options 初始化之前注册（Fabric 的 KeyMappingHelper 会校验这一点）。
        com.buwan.shizuku.client.JudgmentControls.register();

        MenuScreens.register(ModMenus.SHIZUKU_CRAFT_TABLE.get(), ShizukuCraftScreen::new);
        WorldsFavoriteClient.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientTickEvent.Post event = new ClientTickEvent.Post();
            GalaxyFieldRenderer.onClientTick(event);
            TimeStopRenderer.getInstance().onClientTick(event);
            VoidPrisonRenderer.getInstance().onClientTick(event);
            YmsaChargeHandler.onClientTick(event);
            FieldActivateInput.tick(client);
            // 审判系统：面板快捷键、幻影剑中键兜底轮询、视觉状态清理、连招时间轴
            com.buwan.shizuku.client.JudgmentControls.tick();
            com.buwan.shizuku.client.SummonSwordInput.tick();
            com.buwan.shizuku.client.JudgmentVisuals.tick();
            com.buwan.shizuku.sa.SaTimeline.tickClient(client);
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

        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            if (context.poseStack() == null) {
                return;
            }
            RenderLevelStageEvent event = new RenderLevelStageEvent(
                    context.poseStack(), Minecraft.getInstance().gameRenderer.getMainCamera(),
                    RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS);
            GalaxyFieldRenderer.onRenderLevel(event);
            TimeStopRenderer.getInstance().onRenderLevel(event);
            VoidPrisonRenderer.getInstance().onRenderLevel(event);
        });

        // 合成波全屏后处理：对应 NeoForge 的 AFTER_CUTOUT_BLOCKS，26.1 用 AFTER_SOLID_FEATURES 近似
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(context -> {
            if (context.poseStack() == null) {
                return;
            }
            SynthwaveShaderRenderer.onRenderWorld(new RenderLevelStageEvent(
                    context.poseStack(), Minecraft.getInstance().gameRenderer.getMainCamera(),
                    RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS, null));
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("shizuku", "galaxy_field"),
                (graphics, deltaTracker) -> GalaxyFieldRenderer.onRenderGuiPost(new RenderGuiEvent.Post()));
        // 26.1 的 HudElement#extractRenderState 直接给 GuiGraphicsExtractor，正好对上两个覆盖层的签名。
        // 顺序：先时停（全局去饱和/暗角），再审判红染（局部、更强的氛围）
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("shizuku", "screen_effects"),
                (graphics, deltaTracker) -> {
                    com.buwan.shizuku.client.TimeStopScreenEffect.getInstance().renderOverlay(graphics);
                    com.buwan.shizuku.client.JudgmentVisuals.shade(
                            graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
                });
        // 宇宙刀手持星光（26.1：不用着色器，走 UV 滚动装饰器）
        com.buwan.shizuku.client.CosmicBladeRenderer.register();
    }
}
