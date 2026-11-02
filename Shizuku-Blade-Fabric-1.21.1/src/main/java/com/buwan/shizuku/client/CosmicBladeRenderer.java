package com.buwan.shizuku.client;

import com.buwan.shizuku.client.shader.CosmicShaders;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.item.ItemStack;

/**
 * 宇宙刀「拿在手上」的星光覆盖层。
 *
 * <p>SlashBlade 渲染刀模型时会为每个 target 分组派发 {@link RenderOverrideEvent}；
 * 原版在 target 以 {@code _luminous} 结尾时，用去掉后缀的基础分组再画一遍 cosmic 着色器，
 * 于是刀身上叠了一层流动星光。移植时漏了这个监听，所以只有斩击/飞剑有特效、手持模型没有。</p>
 */
public final class CosmicBladeRenderer {

    private CosmicBladeRenderer() {
    }

    /** GUI 物品渲染的嵌套深度（>0 表示当前正在画物品图标）。 */
    private static int guiItemDepth;

    public static void enterGuiItemRender(boolean gui) {
        if (gui) {
            guiItemDepth++;
        }
    }

    public static void exitGuiItemRender(boolean gui) {
        if (gui && guiItemDepth > 0) {
            guiItemDepth--;
        }
    }

    public static void register() {
        RenderOverrideEvent.CALLBACK.register(CosmicBladeRenderer::onRenderOverride);
    }

    private static void onRenderOverride(RenderOverrideEvent event) {
        // GUI 里的物品图标不画：那里没有世界矩阵，cosmic 层会被放大成铺满屏幕的星空并随视角闪烁
        if (guiItemDepth > 0) {
            return;
        }
        ItemStack stack = event.getStack();
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemSlashBlade)) {
            return;
        }
        String key = CapabilitySlashBlade.getBladeState(stack)
                .map(s -> s.getTranslationKey())
                .orElse("");
        if (!CosmicShaders.COSMIC_BLADE_KEY.equals(key)) {
            return;
        }
        String target = event.getTarget();
        if (target == null || !target.endsWith("_luminous")) {
            return;
        }
        String baseTarget = target.substring(0, target.length() - "_luminous".length());
        CosmicShaders.renderCosmic(event.getModel(), baseTarget, event.getPoseStack(), event.getBuffer(), 1.0F);
    }
}
