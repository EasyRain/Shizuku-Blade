package com.buwan.shizuku.client.slash;

import com.buwan.shizuku.client.CosmicFxFlag;
import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import mods.flammpfeil.slashblade.client.renderer.entity.state.DriveRenderState;
import mods.flammpfeil.slashblade.client.renderer.entity.state.SlashEffectRenderState;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 普通刀光（26.1）：<b>照抄上游 SlashBlade 的画法</b>，只服务「施法者手持我们自己的刀」。
 *
 * <h2>上游怎么画（反编译 DriveRenderer / SlashEffectRenderer 得到）</h2>
 * <ul>
 *   <li><b>刀光（drive）</b>：模型 {@code slashblade:model/util/drive.obj} 的 {@code base} 组，
 *       贴图 {@code slashblade:model/util/ss.png}；姿态链
 *       {@code YP(-rotLerp-90) → ZP(rotLerp) → XP(rotationRoll) → scale(max(1,baseSize)*0.02) → YP(90)}；</li>
 *   <li><b>斩击（slash effect）</b>：模型 {@code slashblade:model/util/slash.obj} 的 {@code base} 组，
 *       贴图 {@code slashblade:model/util/slash.png}；姿态链
 *       {@code YP(-lerp-90) → ZP(lerp) → XP(roll) → YP(offset-135*progress) → scale(1,.25,1) → scale(1.2)
 *       → scale(baseSize*lerp(.03,.035), .03, ·)}，并在 tessellate 前设
 *       {@code Face.setAlphaOverride(alphaOverrideYZZ)} / {@code Face.setUvOperator} / {@code BladeRenderState.setCol}。</li>
 * </ul>
 *
 * <h2>我们做的两处适配（不是自创几何，只是让上游画法在 26.1 生效）</h2>
 * <ol>
 *   <li><b>{@code Face} / {@code BladeRenderState} 的静态状态必须放进 λ 内部</b>：26.1 的
 *       {@code submitCustomGeometry} 只是把 λ 存起来、<b>帧末</b>才执行；上游在「提交」时设、立刻复位，
 *       等 λ 跑起来时已经全被复位（alpha 覆写与 UV 偏移丢失）。</li>
 *   <li><b>不写深度 + 加法混合 + order(1)</b>：26.1 的批提交不保序，沿用 {@code CosmicShaders}
 *       已经验证过的结论，避免和本体层抢深度。</li>
 * </ol>
 *
 * <h2>诊断开关（一次性定位用）</h2>
 * <pre>
 *   -Dshizuku.slashfx.debug=true     打印 [slashfx] 全流程（extract 标记 / 模型 / 贴图 / RenderType / λ）
 *   -Dshizuku.slashfx.replace_drive=true  刀光允许「整层替换」上游（默认 false = 叠加，绝不 cancel）
 * </pre>
 */
public final class UpstreamSlashFx {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpstreamSlashFx.class);

    /** 调试开关：-Dshizuku.slashfx.debug=true。 */
    // 诊断期默认打开（拿到日志后改回 Boolean.getBoolean(...)）
    private static final boolean DEBUG = true;
    /** 是否允许整层替换上游刀光层（默认关闭 = 只叠加，保证最坏情况仍能看到上游那层）。 */
    private static final boolean REPLACE_DRIVE = Boolean.getBoolean("shizuku.slashfx.replace_drive");
    /** 探针模式：不画模型，只在实体位置画一个 2×2 贴图四边形，用来确认「管线+贴图」通不通。 */
    private static final boolean PROBE = Boolean.getBoolean("shizuku.slashfx.probe");

    /** 满亮度（与上游 / {@code CosmicShaders.MAX_LIGHT} 一致）。 */
    private static final int MAX_LIGHT = 15728880;

    private static final Identifier DRIVE_MODEL = Identifier.fromNamespaceAndPath("slashblade", "model/util/drive.obj");
    private static final Identifier SLASH_MODEL = Identifier.fromNamespaceAndPath("slashblade", "model/util/slash.obj");

    /**
     * 刀光贴图 —— <b>直接用上游的 id</b>。
     *
     * <p>26.1 的 {@code TextureContents.load(rm, id)} 是**原样**拿 id 去 {@code getResourceOrThrow}
     * （不像旧版会自动加 {@code textures/} 前缀），而 {@code TextureManager.getTexture(id)}
     * 对未知 id 会懒建 {@code SimpleTexture} 并按同一 id 读文件；上游这两个 id 正好指向 jar 里真实存在的
     * {@code assets/slashblade/model/util/{ss,slash}.png}，所以能读到。上一版自己注册
     * {@code ReloadableTexture} 是多余的一层（还多了一个可能静默失败的环节），已删除。</p>
     */
    private static final Identifier DRIVE_TEXTURE = Identifier.fromNamespaceAndPath("slashblade", "model/util/ss.png");
    private static final Identifier SLASH_TEXTURE = Identifier.fromNamespaceAndPath("slashblade", "model/util/slash.png");

    private static final float DAOGUANG_SCALE = 0.02F;

    private static RenderType driveRenderType;
    private static RenderType slashRenderType;
    private static boolean renderTypeFailed;

    /** λ 内一旦抛异常就永久停用（避免「取消了上游又画不出来」），并在日志里留一条全栈。 */
    private static volatile boolean disabled;
    /** 调试去重键（只用来限流日志）。 */
    private static final java.util.Set<String> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private UpstreamSlashFx() {
    }

    /** 下游（mixin）用：是否允许 cancel 上游刀光层。 */
    public static boolean mayReplaceDrive() {
        return REPLACE_DRIVE && !disabled;
    }

    /** extract 段的一次性诊断日志（由两个 renderer mixin 调用）。 */
    public static void debugExtract(String kind, Object shooter, CosmicFxFlag flag) {
        if (!DEBUG || flag == null) {
            return;
        }
        if (!LOGGED.add("extract|" + kind)) {
            return;
        }
        LOGGER.info("[slashfx] extract kind={} shooter={} cosmic={} own={} baseSize={}",
                kind,
                shooter instanceof net.minecraft.world.entity.Entity e ? e.getClass().getSimpleName() : String.valueOf(shooter),
                flag.shizuku$isCosmicFx(), flag.shizuku$isOwnBladeFx(), flag.shizuku$getBaseSize());
    }

    // ------------------------------------------------------------------ 刀光（drive）

    /**
     * @return true = 我们<b>确实</b>提交了几何（调用方才能 cancel 上游）；
     *         false = 任何环节不满足（标记/寿命/模型/组/贴图/RenderType/异常），<b>必须</b>交还上游。
     */
    public static boolean submitDrive(DriveRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        if (!(state instanceof CosmicFxFlag flag) || !flag.shizuku$isOwnBladeFx()) {
            return false;
        }
        float lifetime = state.lifetime;
        if (lifetime <= 0.0F) {
            return false;
        }
        float partialTick = partialTickOf(state);
        double baseAlpha = Math.min(lifetime, (double) Math.max(0.0F, lifetime - (float) state.tickCount)) / lifetime;
        baseAlpha = Math.max(0.0, -Math.pow(baseAlpha - 1.0, 4.0) + 0.75);
        if (baseAlpha <= 0.01) {
            return false;
        }
        WavefrontObject model = BladeModelManager.getInstance().getModel(DRIVE_MODEL);
        if (!hasBaseGroup("drive", model)) {
            return false;
        }
        RenderType renderType = renderType(DRIVE_TEXTURE, true);
        if (renderType == null) {
            return false;
        }
        int color = withAlpha(state.color, (float) baseAlpha);
        debugOnce("drive|state", "state lifetime={} tick={} color=#{}, alpha={}",
                lifetime, state.tickCount, Integer.toHexString(state.color), baseAlpha);

        poseStack.pushPose();
        try {
            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, state.yRotO, state.yRot) - 90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.rotLerp(partialTick, state.xRotO, state.xRot)));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.rotationRoll));
            poseStack.scale(DAOGUANG_SCALE, DAOGUANG_SCALE, DAOGUANG_SCALE);
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            return submit(poseStack, collector, renderType, model, color, true);
        } catch (Throwable t) {
            fail("drive", t);
            return false;
        } finally {
            poseStack.popPose();
        }
    }

    // ------------------------------------------------------------------ 斩击（slash effect）

    /** 斩击特效：上游是「追加一层」，我们同样只叠加、不取消上游原层。 */
    public static boolean submitSlashEffect(SlashEffectRenderState state, PoseStack poseStack,
                                            SubmitNodeCollector collector) {
        if (!(state instanceof CosmicFxFlag flag) || !flag.shizuku$isOwnBladeFx()) {
            return false;
        }
        int lifetime = state.lifetime;
        if (lifetime <= 0) {
            return false;
        }
        float partialTick = partialTickOf(state);
        float progress = Math.min((float) lifetime, (float) state.tickCount + partialTick) / (float) lifetime;
        double baseAlpha = Math.min(lifetime, (double) Math.max(0.0F, (float) (lifetime - state.tickCount) - partialTick))
                / (double) lifetime;
        baseAlpha = -Math.pow(baseAlpha - 1.0, 4.0) + 1.0;
        if (baseAlpha <= 0.01) {
            return false;
        }
        WavefrontObject model = BladeModelManager.getInstance().getModel(SLASH_MODEL);
        if (!hasBaseGroup("slash", model)) {
            return false;
        }
        RenderType renderType = renderType(SLASH_TEXTURE, false);
        if (renderType == null) {
            return false;
        }
        int color = withAlpha(state.color, (float) baseAlpha);
        debugOnce("slash|state", "state lifetime={} tick={} color=#{} baseSize={} alpha={}",
                lifetime, state.tickCount, Integer.toHexString(state.color), state.baseSize, baseAlpha);

        poseStack.pushPose();
        try {
            poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partialTick, state.yRotO, state.yRot) - 90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, state.xRotO, state.xRot)));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.rotationRoll));
            poseStack.mulPose(Axis.YP.rotationDegrees(state.rotationOffset - 135.0F * progress));
            poseStack.scale(1.0F, 0.25F, 1.0F);
            poseStack.scale(1.2F, 1.2F, 1.2F);
            float scale = state.baseSize * Mth.lerp(progress, 0.03F, 0.035F);
            poseStack.scale(scale, 0.03F, scale);
            return submit(poseStack, collector, renderType, model, color, false);
        } catch (Throwable t) {
            fail("slash", t);
            return false;
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 真正的提交。{@code Face} 的全局算子在 <b>lambda 内部</b>设置/还原 —— 这是修 bug 的关键，
     * 26.1 的这个 lambda 要到帧末才执行。
     *
     * @return true = 已成功入队
     */
    private static boolean submit(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType,
                                  WavefrontObject model, int color, boolean drive) {
        if (PROBE) {
            return submitProbe(poseStack, collector, drive);
        }
        debugMatrix(drive ? "drive" : "slash", poseStack);
        collector.order(1).submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            try {
                Face.alphaOverride = drive ? Face.alphaNoOverride : Face.alphaOverrideYZZ;
                Face.uvOperator = Face.uvDefaultOperator;
                BladeRenderState.setCol(color);
                PoseStack local = new PoseStack();
                local.last().pose().set(pose.pose());
                local.last().normal().set(pose.normal());
                model.tessellateOnly(consumer, local, MAX_LIGHT, 0, "base");
                debugOnce("lambda|" + (drive ? "drive" : "slash"),
                        "lambda OK rt={} color=#{}", renderType, Integer.toHexString(color));
            } catch (Throwable t) {
                fail(drive ? "lambda-drive" : "lambda-slash", t);
            } finally {
                Face.alphaOverride = Face.alphaNoOverride;
                Face.uvOperator = Face.uvDefaultOperator;
                BladeRenderState.resetCol();
            }
        });
        return !disabled;
    }

    // ------------------------------------------------------------------ 渲染类型 / 模型 / 贴图

    /** 与上游 {@code getSlashBladeBlendColorWrite} 等价（换 pipeline id、不写深度、加法混合）。 */
    private static RenderType renderType(Identifier textureId, boolean drive) {
        RenderType cached = drive ? driveRenderType : slashRenderType;
        if (cached != null) {
            return cached;
        }
        if (renderTypeFailed) {
            return null;
        }
        try {
            RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                    .withLocation(shizuku.prefix(drive ? "pipeline/slash_glow_drive" : "pipeline/slash_glow"))
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withSampler("Sampler1")
                    .withColorTargetState(new ColorTargetState(java.util.Optional.of(BlendFunction.LIGHTNING),
                            ColorTargetState.WRITE_COLOR))
                    // ⚠️ 必须关剔除：上游 OBJ 的三角绕向不保证是逆时针，ENTITY_EMISSIVE_SNIPPET
                    // 默认 withCull(true) ⇒ 整层被背面剔除 =「λ 执行了但屏幕空白」（CosmicShaders 同样关了）。
                    .withCull(false)
                    .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.TRIANGLES)
                    // 加法叠层不写深度（同 CosmicShaders 的踩坑结论：26.1 批提交不保序）
                    .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                    .build();
            warmTexture(textureId);
            RenderSetup setup = RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", textureId)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .useLightmap()
                    .useOverlay()
                    .sortOnUpload()
                    .createRenderSetup();
            RenderType created = RenderType.create(drive ? "shizuku_slash_glow_drive" : "shizuku_slash_glow", setup);
            if (drive) {
                driveRenderType = created;
            } else {
                slashRenderType = created;
            }
            debugOnce("rt|" + textureId, "RenderType OK pipeline={} texture={}", pipeline.getLocation(), textureId);
            return created;
        } catch (Throwable t) {
            renderTypeFailed = true;
            fail("rendertype", t);
            return null;
        }
    }

    /** 模型 + {@code base} 组是否都在（缺一个就一定会画出空白，直接交还上游）。 */
    private static boolean hasBaseGroup(String kind, WavefrontObject model) {
        if (model == null) {
            failOnce("model|" + kind, "[slashfx] 模型为空 kind={}（BladeModelManager 没解析出来）", kind);
            return false;
        }
        try {
            if (model.groupObjects != null) {
                for (var group : model.groupObjects) {
                    if ("base".equals(group.name)) {
                        debugOnce("model|" + kind, "model OK kind={} groups={}", kind, model.groupObjects.size());
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            fail("model-group", t);
            return false;
        }
        failOnce("model-base|" + kind, "[slashfx] 模型里找不到 base 组 kind={}", kind);
        return false;
    }

    /**
     * 预加载贴图：{@code TextureManager.getTexture(id)} 对未知 id 会懒创建一个 {@code SimpleTexture}
     * 并按**同一个 id** 去 {@code assets/<ns>/<path>} 读文件（26.1 不加 {@code textures/} 前缀），
     * 正好命中 {@code assets/slashblade/model/util/*.png}。
     *
     * <p>提前走这一遍，可以避免「RenderSetup 绑定 Sampler0 时贴图还不存在 ⇒ 整层被静默丢弃」，
     * 同时也把「到底读到没有」打进日志。</p>
     */
    private static void warmTexture(Identifier id) {
        try {
            var texture = Minecraft.getInstance().getTextureManager().getTexture(id);
            String info = texture == null ? "null" : texture.getClass().getSimpleName();
            String size = "";
            if (texture instanceof net.minecraft.client.renderer.texture.DynamicTexture dyn
                    && dyn.getPixels() != null) {
                size = dyn.getPixels().getWidth() + "x" + dyn.getPixels().getHeight();
            }
            debugOnce("tex|" + id, "texture ready id={} impl={} {} (resource={})", id, info, size,
                    Minecraft.getInstance().getResourceManager().getResource(id).isPresent() ? "present" : "MISSING");
        } catch (Throwable t) {
            LOGGER.error("[slashfx] 贴图预加载失败 id={}（资源是否存在于 assets/<ns>/<path>？）", id, t);
        }
    }

    /**
     * 探针：在实体本地空间画一个 2×2 的贴图四边形（`RenderType.entityTranslucentEmissive`）。
     *
     * <p>目的：把「管线/贴图/提交目标」与「模型/姿态/顶点格式」彻底分开。
     * 探针可见 ⇒ 管线与提交目标没问题，问题在模型或姿态；探针也不可见 ⇒ 问题在提交目标/阶段。</p>
     */
    private static boolean submitProbe(PoseStack poseStack, SubmitNodeCollector collector, boolean drive) {
        Identifier texture = drive ? DRIVE_TEXTURE : SLASH_TEXTURE;
        RenderType probeType = net.minecraft.client.renderer.rendertype.RenderTypes
                .entityTranslucentEmissive(texture);
        debugOnce("probe|" + (drive ? "drive" : "slash"), "PROBE quad submitted texture={} rt={}", texture, probeType);
        debugMatrix("probe-" + (drive ? "drive" : "slash"), poseStack);
        collector.order(1).submitCustomGeometry(poseStack, probeType, (pose, vc) -> {
            try {
                // 本地空间：面前 2 格、2×2，足够大，肯定看得见
                quadVertex(vc, pose, -1.0F, 0.0F, 2.0F, 0.0F, 1.0F);
                quadVertex(vc, pose, 1.0F, 0.0F, 2.0F, 1.0F, 1.0F);
                quadVertex(vc, pose, 1.0F, 2.0F, 2.0F, 1.0F, 0.0F);
                quadVertex(vc, pose, -1.0F, 2.0F, 2.0F, 0.0F, 0.0F);
            } catch (Throwable t) {
                fail("probe-lambda", t);
            }
        });
        return !disabled;
    }

    /** ENTITY 顶点格式：position + color + uv0 + overlay + light + normal，一项都不能少。 */
    private static void quadVertex(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose pose,
                                   float x, float y, float z, float u, float v) {
        vc.addVertex(pose, x, y, z)
                .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                .setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(MAX_LIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    /** 把最终矩阵的缩放/平移打进日志，确认没被姿态缩成 0.000x。 */
    private static void debugMatrix(String kind, PoseStack poseStack) {
        if (!DEBUG || !LOGGED.add("matrix|" + kind)) {
            return;
        }
        var m = poseStack.last().pose();
        float sx = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
        float sy = (float) Math.sqrt(m.m10() * m.m10() + m.m11() * m.m11() + m.m12() * m.m12());
        float sz = (float) Math.sqrt(m.m20() * m.m20() + m.m21() * m.m21() + m.m22() * m.m22());
        LOGGER.info("[slashfx] matrix kind={} scale=({},{},{}) translate=({},{},{})", kind, sx, sy, sz,
                m.m30(), m.m31(), m.m32());
    }

    private static void debugOnce(String key, String format, Object... args) {
        if (DEBUG && LOGGED.add(key)) {
            LOGGER.info("[slashfx] " + format, args);
        }
    }

    private static void failOnce(String key, String format, Object... args) {
        if (LOGGED.add(key)) {
            LOGGER.error("[slashfx] " + format, args);
        }
    }

    private static void fail(String where, Throwable t) {
        disabled = true;
        LOGGER.error("[slashfx] {} 失败 ⇒ 停用自绘刀光，改由上游照常渲染（最坏情况=改动前）", where, t);
    }

    private static float partialTickOf(Object renderState) {
        return ((cn.sh1rocu.slashblade.api.mixin.IEntityRenderStatePartialTick) renderState).sb$partialTick();
    }

    /** 把寿命 alpha 折进顶点色（保留刀本色的 RGB）。 */
    private static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int rgb = (color & 0xFFFFFF) == 0 ? 0xFFFFFF : color;
        return ARGB.color(a, rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
    }

    /** 只要 debug 打开就给一次「客户端在跑」的确认（便于确认日志确实来自本帧）。 */
    static {
        if (DEBUG) {
            LOGGER.info("[slashfx] debug=true replace_drive={} mc={}", REPLACE_DRIVE,
                    Minecraft.getInstance() == null ? "null" : "ok");
        }
    }
}
