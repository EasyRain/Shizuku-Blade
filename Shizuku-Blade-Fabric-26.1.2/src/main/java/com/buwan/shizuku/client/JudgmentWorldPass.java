package com.buwan.shizuku.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * 「审判」世界几何（域球 + 空间裂缝）的专属帧通道（26.1，1.20.1/1.21.1 世界几何方式的回归）。
 *
 * <p><b>为什么需要独立 FramePass</b>：26.1 的 main pass 是分层的（实体/物品/粒子各有专属
 * target，最后由 {@code minecraft:transparency} 合成链按每像素深度排序混合）。之前把球面画在
 * {@code AFTER_TRANSLUCENT_TERRAIN}/{@code END_MAIN} 时，几何会被卷进分层目标的预乘合成，
 * 观感是「天空区域只剩 20~30% 不透明」。这里改为在<b>合成链完成之后</b>开一个自己的帧通道
 * （照 {@code addLateDebugPass} 的写法），直写 main 的颜色 + 深度 override：
 * main 颜色已是最终画面，天空/半透明层不可能再盖上来；域球用
 * {@link com.buwan.shizuku.client.shader.JudgmentShaders#domain()} 的
 * LESS_THAN_OR_EQUAL 深度测试（不写深度）对着 main 深度画 = NeoForge 1.21.1
 * {@code AFTER_ENTITIES} 的遮挡语义。</p>
 *
 * <p><b>落剑深度的合并</b>：落剑经 {@code COLLECT_SUBMITS} 走原版物品提交节点，画进
 * item_entity 分层（深度留在该层，合成链只写颜色回 main）——main 深度里没有它，
 * 域球会把已合成的落剑「墨洗」掉。解法：主通道在物品层绘制前执行过
 * {@code itemEntity.copyDepthFrom(main)}（见 {@code addMainPass}），所以 item_entity
 * 的深度 = 地形 + 实心实体 + 物品层 全部深度；画球前把它 {@code copyDepthFrom} 回 main，
 * 域球/裂缝即可被地形、实体、落剑正确遮挡（多人时其他玩家的剑同理，等价 1.21.1
 * 「剑先画 + 写深度，球再 LEQUAL 测试」的次序语义）。</p>
 *
 * <p>调用点：{@code LevelRendererSynthwaveMixin} 在 {@code addLateDebugPass}
 * <b>之前</b>（同一 WrapOperation 内），即执行序 = … → 透明合成链 → <b>本通道</b> →
 * late_debug → 合成波后处理链。</p>
 */
public final class JudgmentWorldPass {

    private JudgmentWorldPass() {
    }

    /**
     * 由 {@code LevelRendererSynthwaveMixin} 每帧调用。
     *
     * @param fog   late_debug 用的雾切片（本类着色器不读雾，但保持与 vanilla 帧通道一致的绑定顺序）
     */
    public static void addToFrame(FrameGraphBuilder builder, LevelTargetBundle targets,
                                  CameraRenderState camera, GpuBufferSlice fog) {
        if (builder == null || targets == null || camera == null) {
            return;
        }
        if (targets.main == null || camera.pos == null) {
            return;
        }
        boolean world = JudgmentVisuals.hasWorldGeometry();
        if (!world && !JudgmentRiftPreview.isActive()) {
            return; // 没有活跃施法、预览页签也不可见 ⇒ 不建 pass
        }

        FramePass pass = builder.addPass("shizuku_judgment_world");
        targets.main = pass.readsAndWrites(targets.main);
        // 读 item_entity 分层：保持资源存活 + executes 里做深度合并。合成链是它此刻的
        // 最后一个读取方，不声明的话帧图可能在本通道执行前就回收它。
        final ResourceHandle<RenderTarget> itemEntity = targets.itemEntity;
        if (itemEntity != null) {
            pass.reads(itemEntity);
        }

        pass.executes(() -> {
            RenderSystem.setShaderFog(fog);
            RenderTarget mainTarget = targets.main.get();
            if (world) {
                // 深度合并：main ← item_entity（地形 + 实心实体 + 物品层的深度）。
                // 之后 renderWorld 里的 LEQUAL 测试即对全部近景几何生效。
                if (itemEntity != null) {
                    mainTarget.copyDepthFrom(itemEntity.get());
                }
                // ⚠️ 相机旋转已在全局栈里：renderLevel 建帧图前 push 了 modelViewMatrix
                //（LevelRenderer.java 481-483），帧图执行完才 pop；RenderType.draw 自动取
                // RenderSystem.getModelViewMatrix()。这里【不能】再推一次，否则双重旋转。
                RenderSystem.outputColorTextureOverride = mainTarget.getColorTextureView();
                RenderSystem.outputDepthTextureOverride = mainTarget.getDepthTextureView();
                try {
                    float partialTick = Minecraft.getInstance().getDeltaTracker()
                            .getGameTimeDeltaPartialTick(false);
                    // 相机相对坐标系（与主通道一致）：fresh PoseStack（ModelViewMat 已含相机旋转），
                    // 几何坐标一律传「世界坐标 - camera.pos」。
                    JudgmentVisuals.renderWorld(new PoseStack(), camera.pos, partialTick);
                } finally {
                    RenderSystem.outputColorTextureOverride = null;
                    RenderSystem.outputDepthTextureOverride = null;
                }
            }
            // 面板预览裂缝：用真 judgment_rift 着色器画进离屏目标（GUI 只 blit）
            JudgmentRiftPreview.renderOffscreen(camera.projectionMatrix);
        });
    }
}
