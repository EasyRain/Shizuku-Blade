package com.buwan.shizuku.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/**
 * 在 Fabric 端绘制世界几何（球体 / 圆盘）前的状态修正。
 *
 * <p><b>为什么需要它</b>：本端的 {@code WorldRenderEvents.AFTER_TRANSLUCENT} 是在
 * {@code LevelRenderer} 的 {@code beforeClouds} 钩子上派发的（注入点在云渲染之前），
 * 既不等于原版 {@code RenderType.translucent()} 的状态语义，也和 NeoForge 的
 * {@code Stage.fromRenderType} 派发点不同 ⇒ <b>那一刻的环境 GL 状态不可依赖</b>。</p>
 *
 * <p>最现实的触发者是 <b>Sodium</b>：0.8+ 用 {@code @Overwrite} 整个替换了
 * {@code LevelRenderer.renderSectionLayer}，方法体换成
 * {@code RenderDevice.enterManagedCode()} → {@code setupTerrain}/{@code drawChunkLayer}
 * → {@code exitManagedCode()}，<b>不再调用 {@code RenderType.setupRenderState()}</b>；
 * 而 {@code LEQUAL_DEPTH_TEST.setupState()}（即 {@code enableDepthTest()}）正是在那里执行的。
 * 于是继承到的是 Sodium 画完区块后的状态 ⇒ 球失去遮挡。
 * （NeoForge 1.21.1 端就是这么中招的；那边加上本方法后恢复。）</p>
 *
 * <p><b>不装 Sodium 时也安全</b>：本方法只是把「画不透明/半透明世界几何应当具备的状态」
 * 显式声明一遍 —— 原版语义下本来就是 LEQUAL 深度测试开启、且绘制目标是主 framebuffer，
 * 因此这里两步都是幂等的，无条件调用不会改变原版行为。</p>
 */
final class WorldRenderCompat {

    private WorldRenderCompat() {
    }

    /**
     * 在绘制世界几何（球体等）之前调用，确保画在主 framebuffer 上并带 LEQUAL 深度测试。
     */
    static void bindMainTargetWithDepthTest() {
        // ① 主 framebuffer（Fabulous 下环境里可能是 translucentTarget）
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        // ② 深度测试显式开启并用 LEQUAL（与 RenderType 的默认 LEQUAL_DEPTH_TEST 一致）
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }
}
