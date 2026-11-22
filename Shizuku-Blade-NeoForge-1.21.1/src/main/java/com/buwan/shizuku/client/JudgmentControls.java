package com.buwan.shizuku.client;

import com.buwan.shizuku.client.screen.JudgmentScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * 审判面板的快捷键（NeoForge 1.21.1）。
 *
 * <p>1.21.1 的 tick 事件没有 {@code Phase}，而是拆成 {@code ClientTickEvent.Pre/Post}，
 * 所以这里直接订阅 {@code Post}。</p>
 */
@EventBusSubscriber(modid = "shizuku", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class JudgmentControls {
    public static final KeyMapping OPEN = new KeyMapping(
        "key.shizuku.judgment", InputConstants.Type.KEYSYM, InputConstants.KEY_J, "key.categories.shizuku"
    );

    private JudgmentControls() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    @EventBusSubscriber(modid = "shizuku", value = Dist.CLIENT)
    public static final class Input {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (OPEN.consumeClick() && minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new JudgmentScreen());
            }
        }
    }
}
