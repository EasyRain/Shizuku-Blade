package com.buwan.shizuku.client;

import com.buwan.shizuku.client.screen.JudgmentScreen;
import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * 审判面板的快捷键。
 *
 * <p>Forge 版用 {@code RegisterKeyMappingsEvent} + {@code ClientTickEvent} 订阅；
 * Fabric 版在 {@code ShizukuFabricClient#registerClientReceivers()} 里调 {@link #register()} 注册键位，
 * 每帧由 {@code ClientTickEvents.END_CLIENT_TICK} 调 {@link #tick()}。</p>
 *
 * <p>26.1 迁移：{@code KeyMapping} 的第 4 个参数从「分类名字符串」变成了
 * {@code KeyMapping.Category}（记录），标签按 {@code key.category.<命名空间>.<路径>} 取；
 * 注册入口也从 fabric-api 的 {@code keybinding.v1.KeyBindingHelper} 换成了
 * {@code keymapping.v1.KeyMappingHelper}（26.1 的 fabric-api 只保留了后者）。</p>
 */
public final class JudgmentControls {

    /**
     * 键位分类。用 {@code shizuku:judgment} ⇒ 标签键 {@code key.category.shizuku.judgment}
     * （语言文件里补这一行就能显示中文名；缺了只会显示原始键名，不影响功能）。
     */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(shizuku.prefix("judgment"));

    public static final KeyMapping OPEN = new KeyMapping(
        "key.shizuku.judgment", InputConstants.Type.KEYSYM, InputConstants.KEY_J, CATEGORY
    );

    private JudgmentControls() {}

    public static void register() {
        KeyMappingHelper.registerKeyMapping(OPEN);
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
