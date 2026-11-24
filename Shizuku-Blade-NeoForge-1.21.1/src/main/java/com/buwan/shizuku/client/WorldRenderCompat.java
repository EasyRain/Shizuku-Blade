package com.buwan.shizuku.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/**
 * 在 NeoForge 1.21.1 上绘制世界几何（球体 / 圆盘）前的状态修正。
 *
 * <p><b>为什么需要它</b>：时停球与囚笼球曾出现「没有遮挡（透墙可见）」，而同一阶段的
 * 星河领域圆盘一直正常 —— 后者是三者中唯一用<b>自定义 RenderType</b> 绘制的
 * （{@code CosmicShaders.COSMIC_RENDER_TYPE} 显式 {@code setDepthTestState(LEQUAL_DEPTH_TEST)}），
 * 两个球则用裸 {@code Tesselator} + {@code RenderSystem.setShader} + {@code BufferUploader.drawWithShader}，
 * 完全依赖调用点的<b>环境 GL 状态</b>。
 * 26.1.2 端免疫也是同理（走 {@code RenderTypes.debugQuads().draw(...)}，状态由 RenderType 自带）。
 * ⇒ 结论：<b>问题在于「依赖环境状态」本身</b>，而不是某个固定的状态值。</p>
 *
 * <p>环境状态在这个位置确实不可靠：周围的深度状态既受各 {@code RenderType} 的
 * {@code setup/clearRenderState()} 影响（{@code NO_DEPTH_TEST} 的 setup/clear 都是空操作、
 * 不还原；{@code DepthTestStateShard.clearState} 还会主动 {@code disableDepthTest()}），
 * 也受其它 mod 在同一阶段注册的 handler 影响。</p>
 *
 * <p><b>关于 fabulous</b>：NeoForge 的 {@code AFTER_TRANSLUCENT_BLOCKS} 由
 * {@code ClientHooks.dispatchRenderStage(RenderType, ...)} → {@code Stage.fromRenderType(renderType)}
 * 在 {@code LevelRenderer.renderSectionLayer(RenderType.translucent())} <b>内部</b>派发；
 * 而 {@code RenderType.translucent()} 的输出状态 {@code TRANSLUCENT_TARGET} 在
 * {@code Minecraft.useShaderTransparency()}（要求 {@code graphicsMode >= FABULOUS}）下会把目标
 * 绑到 {@code LevelRenderer.translucentTarget}。这不是本次现象的原因（实测画质是高品质/Fancy），
 * 但属于同一位置的真实陷阱 —— NeoForge 的 javadoc 也警告该阶段
 * <i>"although this is called within a fabulous graphics target, it does not function properly
 * in many cases"</i>，故这里一并把目标显式绑回主 framebuffer 作防御。</p>
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
