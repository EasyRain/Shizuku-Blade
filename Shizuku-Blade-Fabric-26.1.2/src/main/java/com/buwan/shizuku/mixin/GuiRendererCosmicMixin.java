package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.shader.CosmicShaders;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1 的 {@code inventoryRender} 等价物（1.20.1/1.21.1 里由 Forge 的
 * {@code ScreenEvent.Render.Pre/Post} + {@code RenderGuiEvent.Pre/Post} 把「正在画 GUI」
 * 这个事实告诉 {@code CosmicShaders.prepareCosmicRender}，GUI 期间的 cosmic 层一律用
 * 固定视角，转世界视角时图标不再闪烁）。
 *
 * <p>26.1 的整段 GUI 渲染（HUD 快捷栏、所有 Screen、JEI、{@code GuiItemAtlas} 的物品
 * 2D 烘焙、oversized 物品的 PIP {@code renderToTexture}）全部发生在
 * {@code GuiRenderer.render(GpuBufferSlice)} 一个调用里：{@code prepare()}（内含
 * {@code prepareItemElements}：oversized → PIP 实时渲染；普通 → 图集 getOrUpdate 烘焙）
 * → {@code draw()}。世界渲染在本帧更早处已完成，因此「render() 执行期间」就是可靠的
 * GUI 上下文信号 —— 期间发出的 {@code RenderOverrideEvent}（无论 item_* 还是 blade 组）
 * 都按图标处理。</p>
 *
 * <p>语义上等价于 1.21.1 NeoForge 的双事件对：Pre=HEAD、Post=RETURN；{@code endFrame}
 * 的 HEAD 再兜底清一次标志（防止异常路径把标志卡在 true，下一帧世界层被误判成图标）。</p>
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererCosmicMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void shizuku$beginGuiRender(GpuBufferSlice slice, CallbackInfo ci) {
        CosmicShaders.beginGuiRender();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void shizuku$endGuiRender(GpuBufferSlice slice, CallbackInfo ci) {
        CosmicShaders.endGuiRender();
    }

    /** 兜底：即使 render() 中途抛异常，帧末的 endFrame 也把标志收回 false。 */
    @Inject(method = "endFrame", at = @At("HEAD"))
    private void shizuku$endGuiRenderOnEndFrame(CallbackInfo ci) {
        CosmicShaders.endGuiRender();
    }
}
