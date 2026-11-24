package com.buwan.shizuku.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/**
 * NeoForge 1.21.1 世界几何绘制的状态修正。
 *
 * <p><b>问题</b>：NeoForge 的 {@code RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS} 与
 * Forge / Fabric 的同名阶段<b>语义不同</b> —— 它不是从
 * {@code LevelRenderer.renderLevel} 里派发的，而是由
 * {@code ClientHooks.dispatchRenderStage(RenderType, ...)} 经
 * {@code Stage.fromRenderType(renderType)} 在
 * {@code LevelRenderer.renderSectionLayer(RenderType.translucent())} <b>内部</b>、
 * 该 RenderType 的 {@code clearRenderState()} <b>之前</b>回调的。</p>
 *
 * <p>而 {@code RenderType.translucent()} 的输出状态 {@code TRANSLUCENT_TARGET} 在
 * Fabulous 画质（{@code Minecraft.useShaderTransparency()}）下会把绘制目标绑到
 * {@code LevelRenderer} 的 {@code translucentTarget}；这个 FBO 的深度只是一次性的
 * {@code copyDepthFrom(main)} 拷贝，在里面画世界几何会丢掉场景深度
 * ⇒ 球体/圆盘不再被方块遮挡。NeoForge 自己的 javadoc 也警告过该阶段：
 * <i>"although this is called within a fabulous graphics target, it does not function
 * properly in many cases"</i>。</p>
 *
 * <p><b>为什么别的端没这个问题</b>：作者原版（Forge 1.20.1）与 Fabric 端
 * （{@code WorldRenderEvents.AFTER_TRANSLUCENT}）都在 {@code renderSectionLayer}
 * <b>返回之后</b>、主 framebuffer 已绑回时才派发同类阶段 —— 那时候主目标上就是场景深度。</p>
 *
 * <p><b>本方法做了什么</b>：把目标显式绑回主 framebuffer，并显式开启 LEQUAL 深度测试
 * （不依赖环境里残留的状态）。非 Fabulous 画质下这两步都是幂等的，
 * 因此可以无条件调用。</p>
 */
final class WorldRenderCompat {

    private WorldRenderCompat() {
    }

    /**
     * 在绘制世界几何（球体 / 圆盘等）之前调用，确保画在主 framebuffer 上并带深度测试。
     */
    static void bindMainTargetWithDepthTest() {
        // ① 绑回主 framebuffer：Fabulous 下此刻绑的是 translucentTarget（见类注释）
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        // ② 深度测试显式开启并用 LEQUAL（与 RenderType 的默认 LEQUAL_DEPTH_TEST 一致）
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }
}
