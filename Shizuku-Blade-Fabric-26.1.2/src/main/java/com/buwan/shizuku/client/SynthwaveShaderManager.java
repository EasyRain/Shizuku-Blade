package com.buwan.shizuku.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 合成波全屏后处理（26.1 原生 post_effect，原版 1.20.1/1.21.1 效果的忠实移植）。
 *
 * <p>管线（照原版 transparency 后处理链）：pass1 读 minecraft:main 的颜色+深度写到内部
 * swap（着色器内部手动完成原版 srcalpha/1-srcalpha 叠加 = {@code mix(scene, synth, alpha)}），
 * pass2 原版 {@code minecraft:post/blit} 写回 minecraft:main。</p>
 *
 * <p>每帧 uniform：26.1 的 {@code PostPass} uniform 构造期固定 ⇒ <b>每帧用代码重建
 * {@code PostChainConfig}</b>（record 构造器全 public，uniform 值经 {@code UniformValue.CODEC}
 * 解析构造）→ {@code PostChain.load} → {@code addToFrame}，下一帧关上一条链。
 * uniform 值与 1.21.1 版完全一致：逆投影/逆视图矩阵来自 {@code CameraRenderState}
 * 的 public 字段（{@code viewRotationMatrix} 等价旧 WorldRenderEvents 的 poseStack pose）、
 * 相机位 {@code pos}、时间 = 秒×3、淡入 = 200ms 延迟后 100ms 渐入。</p>
 */
public final class SynthwaveShaderManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SynthwaveShaderManager.class);

    public static final Identifier SYNTHWAVE_CHAIN_ID = Identifier.fromNamespaceAndPath("shizuku", "synthwave");
    private static final Identifier SCREENQUAD = Identifier.fromNamespaceAndPath("minecraft", "core/screenquad");
    private static final Identifier SYNTHWAVE_FSH = Identifier.fromNamespaceAndPath("shizuku", "post/synthwave");
    private static final Identifier BLIT_FSH = Identifier.fromNamespaceAndPath("minecraft", "post/blit");
    private static final Identifier SWAP = Identifier.fromNamespaceAndPath("shizuku", "swap");
    /** 外部目标集合：本链引用了 minecraft:main（输入+输出）。 */
    private static final Set<Identifier> EXTERNAL_TARGETS = Set.of(PostChain.MAIN_TARGET_ID);

    /** 1.21.1 原版参数：时间流速×3；淡入延迟 200ms + 渐入 100ms（防快速连点闪烁）。 */
    private static final float SPEED_MULTIPLIER = 3.0F;
    private static final long FADE_DELAY_MS = 200L;
    private static final long FADE_MS = 100L;

    private static boolean isSynthwaveEnabled;
    private static long animationStartTime;
    /** 当前帧挂入 FrameGraph 的链；下一帧 renderLevel 时关闭（图已执行完）。 */
    private static PostChain activeChain;
    /** 上一帧的 builder，用于 once-per-frame 去重（builder 每帧新建）。 */
    private static FrameGraphBuilder lastBuilder;

    private SynthwaveShaderManager() {
    }

    /** 由 {@code LevelRendererSynthwaveMixin} 每帧调用（addLateDebugPass 之后）。 */
    public static void addToFrame(FrameGraphBuilder builder, LevelTargetBundle targets, CameraRenderState camera) {
        if (activeChain != null) {
            try {
                activeChain.close();
            } catch (Exception e) {
                LOGGER.warn("Failed to close previous synthwave post chain", e);
            }
            activeChain = null;
        }
        if (!isSynthwaveEnabled || ShaderPackCompat.isShaderPackActive()) {
            return;
        }
        if (builder == lastBuilder) {
            return; // 同一帧内 wrap 命中多次时只挂一次
        }
        ResourceHandle<RenderTarget> main = targets == null ? null : targets.main;
        if (main == null || camera == null || camera.projectionMatrix == null || camera.viewRotationMatrix == null || camera.pos == null) {
            return;
        }
        try {
            RenderTarget mainTarget = main.get();
            long elapsed = System.currentTimeMillis() - animationStartTime;
            float timeSeconds = elapsed / 1000.0F * SPEED_MULTIPLIER;
            float fade = Math.max(Math.min((elapsed - FADE_DELAY_MS) / (float) FADE_MS, 1.0F), 0.0F);

            Matrix4f invProj = new Matrix4f(camera.projectionMatrix).invert();
            Matrix4f invView = new Matrix4f(camera.viewRotationMatrix).invert();
            Vec3 camPos = camera.pos;

            // ⚠️ 顺序必须与 fsh 的 SynthwaveConfig 块声明完全一致（std140 顺序布局）
            List<UniformValue> block = List.of(
                    uval("U_GameTime", "float", new JsonPrimitive(timeSeconds)),
                    uval("U_FadeIn", "float", new JsonPrimitive(fade)),
                    uval("ScreenSize", "vec2", floats(mainTarget.width, mainTarget.height)),
                    // ⚠️ 26.1 的 matrix4x4 uniform 按【行主序】解释（ExtraCodecs.MATRIX4F: setRowColumn(i>>2, i&3, v)），
                    // JOML get() 是列主序 → 必须转成行主序，否则矩阵被转置
                    // （实测症状：黑幕有深度但遮罩瞬间全开 + 网格糊屏 = 转置逆矩阵压缩了距离）
                    uval("U_InverseProjectionMatrix", "matrix4x4", rowMajor(invProj)),
                    uval("U_InverseViewMatrix", "matrix4x4", rowMajor(invView)),
                    uval("U_CameraPosition", "vec3", floats((float) camPos.x, (float) camPos.y, (float) camPos.z)));

            ShaderManager shaderManager = Minecraft.getInstance().getShaderManager();
            PostChain chain = PostChain.load(buildConfig(block),
                    Minecraft.getInstance().getTextureManager(),
                    EXTERNAL_TARGETS, SYNTHWAVE_CHAIN_ID,
                    shaderManager.postChainProjection, shaderManager.postChainProjectionMatrixBuffer);
            lastBuilder = builder;
            chain.addToFrame(builder, mainTarget.width, mainTarget.height,
                    PostChain.TargetBundle.of(PostChain.MAIN_TARGET_ID, main));
            activeChain = chain;
        } catch (Exception e) {
            LOGGER.error("Failed to build/add synthwave post chain", e);
        }
    }

    private static PostChainConfig buildConfig(List<UniformValue> block) {
        List<PostChainConfig.Input> pass1Inputs = List.of(
                new PostChainConfig.TargetInput("MainCol", PostChain.MAIN_TARGET_ID, false, false),
                new PostChainConfig.TargetInput("MainDepth", PostChain.MAIN_TARGET_ID, true, false));
        Map<String, List<UniformValue>> pass1Uniforms = Map.of("SynthwaveConfig", block);
        PostChainConfig.Pass pass1 = new PostChainConfig.Pass(SCREENQUAD, SYNTHWAVE_FSH, pass1Inputs, SWAP, pass1Uniforms);

        List<PostChainConfig.Input> pass2Inputs = List.of(
                new PostChainConfig.TargetInput("In", SWAP, false, false));
        Map<String, List<UniformValue>> pass2Uniforms = Map.of("BlitConfig",
                List.of(uval("ColorModulate", "vec4", floats(1.0F, 1.0F, 1.0F, 1.0F))));
        PostChainConfig.Pass pass2 = new PostChainConfig.Pass(SCREENQUAD, BLIT_FSH, pass2Inputs, PostChain.MAIN_TARGET_ID, pass2Uniforms);

        return new PostChainConfig(
                Map.of(SWAP, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0)),
                List.of(pass1, pass2));
    }

    /** 用 CODEC 从和 json 同构的 {"name","type","value"} 构造 UniformValue（构造器不公开）。 */
    private static UniformValue uval(String name, String type, JsonElement value) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("type", type);
        json.add("value", value);
        return UniformValue.CODEC.parse(JsonOps.INSTANCE, json)
                .result()
                .orElseThrow(() -> new IllegalStateException("Failed to parse uniform " + name + " of type " + type));
    }

    private static JsonArray floats(float... values) {
        JsonArray array = new JsonArray(values.length);
        for (float v : values) {
            array.add(v);
        }
        return array;
    }

    /** JOML 列主序 → 26.1 matrix4x4 codec 要的行主序（本 JOML 版本无 getTransposed(float[])）。 */
    private static JsonArray rowMajor(Matrix4f m) {
        float[] c = m.get(new float[16]); // c[col*4+row]
        return floats(
                c[0], c[4], c[8], c[12],
                c[1], c[5], c[9], c[13],
                c[2], c[6], c[10], c[14],
                c[3], c[7], c[11], c[15]);
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
        animationStartTime = System.currentTimeMillis();
        LOGGER.info("Synthwave shader STARTED (26.1 post_effect chain, per-frame uniforms)");
    }

    public static void stopShader() {
        isSynthwaveEnabled = false;
        LOGGER.info("Synthwave shader STOPPED");
    }

    public static boolean isShaderActive() {
        return isSynthwaveEnabled;
    }
}
