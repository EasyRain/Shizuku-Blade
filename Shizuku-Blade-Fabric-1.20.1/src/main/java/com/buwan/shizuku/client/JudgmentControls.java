package com.buwan.shizuku.client;

import com.buwan.shizuku.client.screen.JudgmentScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * 审判面板的快捷键。
 *
 * <p>Forge 版用 {@code RegisterKeyMappingsEvent} + {@code ClientTickEvent} 订阅；
 * Fabric 版在 {@code ShizukuFabricClient#registerClientReceivers()} 里调 {@link #register()} 注册键位，
 * 每帧由 {@code ClientTickEvents.END_CLIENT_TICK} 调 {@link #tick()}。</p>
 */
public final class JudgmentControls {
    public static final KeyMapping OPEN = new KeyMapping(
        "key.shizuku.judgment", InputConstants.Type.KEYSYM, InputConstants.KEY_J, "key.categories.shizuku"
    );

    private JudgmentControls() {}

    public static void register() {
        KeyBindingHelper.registerKeyBinding(OPEN);
    }

    /** 客户端每 tick 调用：按下快捷键就打开审判面板。 */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new JudgmentScreen());
            }
        }
    }
}
