package com.buwan.shizuku.client;

import com.buwan.shizuku.shizuku;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

/**
 * 审判域球的全屏后处理（26.1）。
 *
 * <p>为什么不用世界几何：26.1 的天空是在世界几何之后绘制的，实测把球面画在
 * {@code AFTER_TRANSLUCENT_TERRAIN} / {@code END_MAIN} 都会被天空盖住（地平线以下实心、
 * 天空部分只剩 20~30% 不透明）。合成波能盖住天空是因为它走的是 {@link PostChain} 后处理，
 * 这里照同一条路：按深度重建视线射线、与域球求交，命中就按 1.20.1 的
 * {@code judgment_domain} 算法算墨焰色再合成。</p>
 *
 * <p>结构完全对照 {@link SynthwaveShaderManager}：pass1 = 我们的 fsh → 写 SWAP，
 * pass2 = 原版 blit 写回 minecraft:main。</p>
 */
public final class JudgmentPostEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(JudgmentPostEffect.class);

    private static final Identifier CHAIN_ID = shizuku.prefix("judgment_domain");
    private static final Identifier SCREENQUAD = Identifier.fromNamespaceAndPath("minecraft", "core/screenquad");
    private static final Identifier SHADER = shizuku.prefix("post/judgment_domain");
    private static final Identifier BLIT_FSH = Identifier.fromNamespaceAndPath("minecraft", "post/blit");
    private static final Identifier SWAP = shizuku.prefix("judgment_swap");

    private static final Set<Identifier> EXTERNAL_TARGETS = Set.of(PostChain.MAIN_TARGET_ID);

    private static PostChain activeChain;
    private static FrameGraphBuilder lastBuilder;
    private static boolean logged;

    private JudgmentPostEffect() {
    }

    /** 由 {@code LevelRendererSynthwaveMixin} 每帧调用（与合成波同一挂点，已经在天空/天气之后）。 */
    public static void addToFrame(FrameGraphBuilder builder, LevelTargetBundle targets, CameraRenderState camera) {
        if (activeChain != null) {
            try {
                activeChain.close();
            } catch (Exception e) {
                LOGGER.warn("Failed to close previous judgment post chain", e);
            }
            activeChain = null;
        }
        java.util.List<double[]> domains = JudgmentVisuals.domainStates();
        double[] rift = JudgmentVisuals.riftState();
        if (domains.isEmpty() && rift == null) {
            return;
        }
        if (builder == lastBuilder) {
            return;
        }
        ResourceHandle<RenderTarget> main = targets == null ? null : targets.main;
        if (main == null || camera == null || camera.projectionMatrix == null
                || camera.viewRotationMatrix == null || camera.pos == null) {
            return;
        }
        try {
            RenderTarget mainTarget = main.get();
            Matrix4f invProj = new Matrix4f(camera.projectionMatrix).invert();
            Matrix4f invView = new Matrix4f(camera.viewRotationMatrix).invert();
            Vec3 camPos = camera.pos;

            // ⚠️ 顺序必须与 fsh 的 JudgmentConfig 块逐项一致（std140 顺序布局）
            java.util.List<UniformValue> block = new java.util.ArrayList<>();
            // ⚠️ 前 6 项顺序必须与 fsh 的 v1 前缀一致（见 fsh 注释）
            double[] mainDomain = domains.isEmpty() ? null : domains.get(0);
            block.add(uval("U_CameraPosition", "vec4", floats((float) camPos.x, (float) camPos.y, (float) camPos.z, 1.0F)));
            block.add(uval("U_DomainCenter", "vec4", mainDomain == null
                    ? floats(0.0F, 0.0F, 0.0F, 0.0F)
                    : floats((float) mainDomain[0], (float) mainDomain[1], (float) mainDomain[2], (float) mainDomain[3])));
            block.add(uval("U_Params", "vec4", mainDomain == null
                    ? floats(0.0F, 0.0F, 0.0F, 0.0F)
                    : floats((float) mainDomain[4], (float) mainDomain[5], 0.0F, 0.0F)));
            block.add(uval("ScreenSize", "vec2", floats(mainTarget.width, mainTarget.height)));
            block.add(uval("U_InverseProjectionMatrix", "matrix4x4", rowMajor(invProj)));
            block.add(uval("U_InverseViewMatrix", "matrix4x4", rowMajor(invView)));
            for (int i = 1; i < 4; i++) {
                double[] d = i < domains.size() ? domains.get(i) : null;
                block.add(uval("U_Domain" + i, "vec4", d == null
                        ? floats(0.0F, 0.0F, 0.0F, 0.0F)
                        : floats((float) d[0], (float) d[1], (float) d[2], (float) d[3])));
            }
            for (int i = 1; i < 4; i++) {
                double[] d = i < domains.size() ? domains.get(i) : null;
                block.add(uval("U_DomainParam" + i, "vec4", d == null
                        ? floats(0.0F, 0.0F, 0.0F, 0.0F)
                        : floats((float) d[4], (float) d[5], 0.0F, 1.0F)));
            }
            boolean hasRift = rift != null;
            block.add(uval("U_RiftCenter", "vec4", hasRift
                    ? floats((float) rift[0], (float) rift[1], (float) rift[2], 0.0F) : floats(0, 0, 0, 0)));
            block.add(uval("U_RiftRight", "vec4", hasRift
                    ? floats((float) rift[3], (float) rift[4], (float) rift[5], 0.0F) : floats(0, 0, 0, 0)));
            block.add(uval("U_RiftUp", "vec4", hasRift
                    ? floats((float) rift[6], (float) rift[7], (float) rift[8], 0.0F) : floats(0, 0, 0, 0)));
            block.add(uval("U_RiftParam", "vec4", hasRift
                    ? floats((float) rift[9], (float) rift[10], (float) rift[11], 1.0F) : floats(0, 0, 0, 0)));

            ShaderManager shaderManager = Minecraft.getInstance().getShaderManager();
            PostChain chain = PostChain.load(buildConfig(block),
                    Minecraft.getInstance().getTextureManager(),
                    EXTERNAL_TARGETS, CHAIN_ID,
                    shaderManager.postChainProjection, shaderManager.postChainProjectionMatrixBuffer);
            lastBuilder = builder;
            chain.addToFrame(builder, mainTarget.width, mainTarget.height,
                    PostChain.TargetBundle.of(PostChain.MAIN_TARGET_ID, main));
            activeChain = chain;
            if (!logged) {
                logged = true;
                LOGGER.info("[judgfx] judgment post chain active domains={} rift={}",
                        domains.size(), rift != null);
            }
        } catch (Exception e) {
            LOGGER.error("[judgfx] failed to build judgment post chain", e);
        }
    }

    private static PostChainConfig buildConfig(List<UniformValue> block) {
        List<PostChainConfig.Input> pass1Inputs = List.of(
                new PostChainConfig.TargetInput("MainCol", PostChain.MAIN_TARGET_ID, false, false),
                new PostChainConfig.TargetInput("MainDepth", PostChain.MAIN_TARGET_ID, true, false));
        Map<String, List<UniformValue>> pass1Uniforms = Map.of("JudgmentConfig", block);
        PostChainConfig.Pass pass1 = new PostChainConfig.Pass(SCREENQUAD, SHADER, pass1Inputs, SWAP, pass1Uniforms);

        List<PostChainConfig.Input> pass2Inputs = List.of(
                new PostChainConfig.TargetInput("In", SWAP, false, false));
        Map<String, List<UniformValue>> pass2Uniforms = Map.of("BlitConfig",
                List.of(uval("ColorModulate", "vec4", floats(1.0F, 1.0F, 1.0F, 1.0F))));
        PostChainConfig.Pass pass2 = new PostChainConfig.Pass(SCREENQUAD, BLIT_FSH, pass2Inputs, PostChain.MAIN_TARGET_ID, pass2Uniforms);

        return new PostChainConfig(
                Map.of(SWAP, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0)),
                List.of(pass1, pass2));
    }

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

    /** JOML 列主序 → 26.1 matrix4x4 codec 要的行主序。 */
    private static JsonArray rowMajor(Matrix4f m) {
        float[] c = m.get(new float[16]);
        return floats(
                c[0], c[4], c[8], c[12],
                c[1], c[5], c[9], c[13],
                c[2], c[6], c[10], c[14],
                c[3], c[7], c[11], c[15]);
    }

    /** 供调试：当前是否有活跃的域球。 */
    public static boolean isActive() {
        return activeChain != null;
    }
}
