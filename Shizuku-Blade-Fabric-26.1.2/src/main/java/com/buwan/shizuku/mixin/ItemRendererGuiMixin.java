package com.buwan.shizuku.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 26.1 阶段一简化：这个 mixin 已经无事可做，保留空壳。
 *
 * <p>1.21.1 的用途：{@code ItemRenderer#render} 的 HEAD/RETURN 上开一个「正在以 GUI 上下文渲染物品」的标记，
 * 让 {@code CosmicBladeRenderer} 区分「世界手持模型」与「GUI 里的图标」，否则物品栏里的刀会画出一大片星空。</p>
 *
 * <p>26.1 的两处变化让它失去意义：</p>
 * <ul>
 *   <li>{@code net.minecraft.client.renderer.entity.ItemRenderer} 整个类已经不存在
 *       （物品渲染改成 {@code ItemModelResolver} + {@code ItemStackRenderState} + {@code SubmitNodeCollector}）；</li>
 *   <li>{@code CosmicBladeRenderer} 在 26.1 的移植里也不再提供
 *       {@code enterGuiItemRender/exitGuiItemRender}（GUI 物品走 {@code GuiItemAtlas}，
 *       与 {@code RenderOverrideEvent} 的手持模型路径天然分离，不再需要这个标记）。</li>
 * </ul>
 *
 * <p>保留这个空 mixin 是因为 {@code shizuku.mixins.json} 的 client 段还列着它；
 * 空 mixin 合法（没有 injector 就不会解析目标方法），不会在启动时报错。</p>
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ItemRendererGuiMixin {
}
