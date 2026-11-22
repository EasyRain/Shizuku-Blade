package com.buwan.shizuku.client;

import com.buwan.shizuku.client.screen.JudgmentScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

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
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (OPEN.consumeClick() && minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new JudgmentScreen());
            }
        }
    }
}
