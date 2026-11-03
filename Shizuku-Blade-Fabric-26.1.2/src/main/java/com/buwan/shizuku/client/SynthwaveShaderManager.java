package com.buwan.shizuku.client;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 合成波全屏后处理（26.1 原生数据驱动实现）。
 *
 * <p>架构（照抄原版 transparency 后处理链，证据见 .ref/dumps/26.1.2-shader/）：
 * <ul>
 *   <li>{@code assets/shizuku/post_effect/synthwave.json}：pass1 读 minecraft:main 的颜色+深度
 *       （{@code use_depth_buffer:true} 是原版 transparency.json 已验证的写法）写到内部 swap；
 *       pass2 用原版 {@code minecraft:post/blit} 把 swap 写回 {@code minecraft:main}。</li>
 *   <li>{@code LevelRendererSynthwaveMixin} 在 renderLevel 的 addLateDebugPass 之后调用
 *       {@link #addToFrame}，把链挂进当帧 FrameGraph（渲染时机 = 世界全部画完之后、UI 之前）。</li>
 *   <li>once-per-frame 防重复：FrameGraphBuilder 每帧都是新实例，用它做去重标记。</li>
 * </ul></p>
 *
 * <p>里程碑 1（当前）：uniform 全为 json 静态值，先验证链路出图。
 * 里程碑 2（TODO）：每帧用代码构造 {@code PostChainConfig}（record 构造器公开，uniform 值可注入
 * {@code U_GameTime}/{@code U_InverseProjectionMatrix}/{@code U_InverseViewMatrix}/{@code U_CameraPosition}），
 * {@code PostChain.load(...)} 后 addToFrame + close，即可复原 1.20.1 的深度重建网格观感。</p>
 */
public final class SynthwaveShaderManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SynthwaveShaderManager.class);

    public static final Identifier SYNTHWAVE_CHAIN_ID = Identifier.fromNamespaceAndPath("shizuku", "synthwave");
    /** 外部目标集合：本链引用了 minecraft:main（输入+输出）。 */
    private static final Set<Identifier> EXTERNAL_TARGETS = Set.of(PostChain.MAIN_TARGET_ID);

    private static boolean isSynthwaveEnabled;
    private static PostChain chain;
    private static boolean loadFailed;
    /** 上一帧的 builder，用于 once-per-frame 去重（builder 每帧新建）。 */
    private static FrameGraphBuilder lastBuilder;

    private SynthwaveShaderManager() {
    }

    /** 由 {@code LevelRendererSynthwaveMixin} 每帧调用（addLateDebugPass 之后）。 */
    public static void addToFrame(FrameGraphBuilder builder, LevelTargetBundle targets) {
        if (!isSynthwaveEnabled || ShaderPackCompat.isShaderPackActive()) {
            return;
        }
        if (builder == lastBuilder) {
            return; // 同一帧内 wrap 命中多次时只挂一次
        }
        ResourceHandle<RenderTarget> main = targets == null ? null : targets.main;
        if (main == null) {
            return;
        }
        PostChain postChain = obtainChain();
        if (postChain == null) {
            return;
        }
        try {
            lastBuilder = builder;
            RenderTarget mainTarget = main.get();
            postChain.addToFrame(builder, mainTarget.width, mainTarget.height,
                    PostChain.TargetBundle.of(PostChain.MAIN_TARGET_ID, main));
        } catch (Exception e) {
            LOGGER.error("Failed to add synthwave post chain to frame", e);
        }
    }

    private static PostChain obtainChain() {
        if (chain != null || loadFailed) {
            return chain;
        }
        try {
            ShaderManager shaderManager = Minecraft.getInstance().getShaderManager();
            chain = shaderManager.getPostChain(SYNTHWAVE_CHAIN_ID, EXTERNAL_TARGETS);
            if (chain == null) {
                LOGGER.warn("Synthwave post chain not found (assets/shizuku/post_effect/synthwave.json missing?)");
                loadFailed = true;
            } else {
                LOGGER.info("Synthwave post chain loaded (26.1 native post_effect)");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load synthwave post chain", e);
            loadFailed = true;
            chain = null;
        }
        return chain;
    }

    /** 26.1 无需在代码里创建 ShaderInstance，保留方法供调用方兼容。 */
    public static Object ensureShader() {
        return null;
    }

    /** 旧渲染入口（WorldRenderEvents 阶段派发）。26.1 改由 mixin 挂 FrameGraph，这里留空。 */
    public static void render(com.mojang.blaze3d.vertex.PoseStack poseStack, org.joml.Matrix4f projectionMatrix) {
        // no-op：见类注释
    }

    public static void startShader() {
        isSynthwaveEnabled = true;
        LOGGER.info("Synthwave shader STARTED (26.1 post_effect chain)");
    }

    public static void stopShader() {
        isSynthwaveEnabled = false;
        LOGGER.info("Synthwave shader STOPPED");
    }

    public static boolean isShaderActive() {
        return isSynthwaveEnabled;
    }
}
