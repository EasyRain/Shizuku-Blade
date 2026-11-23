package com.buwan.shizuku.client.slash;

import com.buwan.shizuku.client.CosmicFxFlag;
import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 普通刀光（26.1）：<b>照抄上游 SlashBlade 的画法</b>，只服务「施法者手持我们自己的刀」。
 *
 * <p>—— 上游是怎么画的（反编译 {@code DriveRenderer} / {@code SlashEffectRenderer} 得到）：</p>
 * <ul>
 *   <li><b>刀光（drive）</b>：模型 {@code slashblade:model/util/drive.obj} 的 {@code base} 组，
 *       贴图 {@code slashblade:model/util/ss.png}，走
 *       {@code BladeRenderState.renderOverridedLuminous}；姿态链是
 *       {@code YP(-rotLerp-90) → ZP(rotLerp) → XP(rotationRoll) → scale(max(1,baseSize)*0.02) → YP(90)}；</li>
 *   <li><b>斩击（slash effect）</b>：模型 {@code slashblade:model/util/slash.obj} 的 {@code base} 组，
 *       贴图 {@code slashblade:model/util/slash.png}，走
 *       {@code BladeRenderState.renderOverridedColorWrite}，并在提交前后
 *       {@code Face.setAlphaOverride(alphaOverrideYZZ)} / {@code Face.setUvOperator(...)} /
 *       {@code BladeRenderState.setCol(color)}。</li>
 * </ul>
 *
 * <p>—— 为什么上游在 26.1 上看不见（查到的确切原因）：</p>
 * <ol>
 *   <li><b>贴图 id 指到了不存在的位置</b>：上游对象里的贴图 id 是
 *       {@code slashblade:model/util/slash.png}（{@code ss.png} 同理），而 jar 里真实的文件在
 *       {@code assets/slashblade/model/util/slash.png} —— <b>没有 {@code textures/} 这一段</b>。
 *       {@code TextureManager} 只自动加载 {@code assets/<ns>/textures/**}，所以这两个 id
 *       <b>从来没被注册过</b>，RenderSetup 的 Sampler0 绑不到图 → 整层画不出来。
 *       本类在首次提交时用 {@link UpstreamPngTexture} 把这两张图按「真实路径」读出来，
 *       注册到<b>我们自己的 id</b>（{@code shizuku:model/util/*_glow}）上
 *       （{@link ReloadableTexture}，资源重载后会自动重新加载）。</li>
 *   <li><b>{@code Face} 的静态算子是全局可变状态，而上游在「提交」时设、立刻又复位</b>：
 *       26.1 的 {@code submitCustomGeometry} 只是把 lambda 存起来、<b>帧末</b>才执行，
 *       等 lambda 真跑起来时 {@code Face.setAlphaOverride/ setUvOperator / setCol} 早被复位了，
 *       于是按 Y/Z 的 alpha 与 UV 偏移全部丢失。本类把这三个设置<b>放进 lambda 内部</b>
 *       （提交前设、执行完在 finally 里还原），这才是 26.1 延迟提交下的正确写法。</li>
 *   <li>顺带把「不写深度 + 加法混合 + order(1)」照我们 {@code CosmicShaders} 已经验证过的做法固定下来，
 *       免得和本体层抢深度/顺序（26.1 的批提交不保序）。</li>
 * </ol>
 *
 * <p>配色：刀光/斩击的顶点色取刀自身的 {@code render state color}（= 拔刀剑的 colorCode），
 * alpha 由寿命曲线给出，所以是「刀本色 × 淡入淡出」。</p>
 */
public final class UpstreamSlashFx {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpstreamSlashFx.class);

    /** 满亮度（与上游 / {@code CosmicShaders.MAX_LIGHT} 一致）。 */
    private static final int MAX_LIGHT = 15728880;

    /** 上游刀光模型（jar 内确实存在：{@code assets/slashblade/model/util/drive.obj}）。 */
    private static final Identifier DRIVE_MODEL = Identifier.fromNamespaceAndPath("slashblade", "model/util/drive.obj");
    /** 上游斩击模型（{@code assets/slashblade/model/util/slash.obj}）。 */
    private static final Identifier SLASH_MODEL = Identifier.fromNamespaceAndPath("slashblade", "model/util/slash.obj");
    /**
     * 我们自己的贴图 id（只是「注册用的钥匙」，文件内容仍然是上游那张图）。
     *
     * <p>刻意<b>不用</b>上游的 id：一旦把 {@code slashblade:model/util/slash.png} 注册进去，
     * 上游自己那条斩击层也会跟着恢复可见（等于替上游改了表现）。用我们自己的命名空间
     * 只让「我们的刀」那层能看到图，上游的刀与上游的渲染表现保持原样。</p>
     */
    private static final Identifier DRIVE_TEXTURE = shizuku.prefix("model/util/drive_glow");
    private static final Identifier SLASH_TEXTURE = shizuku.prefix("model/util/slash_glow");
    /** 图片文件的<b>真实</b>路径（在 slashblade jar 里，没有 textures/ 段，只能走 ResourceManager 直接读）。 */
    private static final Identifier DRIVE_TEXTURE_FILE =
            Identifier.fromNamespaceAndPath("slashblade", "model/util/ss.png");
    private static final Identifier SLASH_TEXTURE_FILE =
            Identifier.fromNamespaceAndPath("slashblade", "model/util/slash.png");

    private static final float DAOGUANG_SCALE = 0.02F;

    /** 已尝试注册过的贴图（每张只注册一次；{@link ReloadableTexture} 会跟着资源重载自己重载）。 */
    private static final Map<Identifier, Boolean> REGISTERED = new ConcurrentHashMap<>();

    private static RenderType slashRenderType;
    private static boolean renderTypeFailed;

    private UpstreamSlashFx() {
    }

    /** 发射者手持的是「我们自己的刀」且<b>不是</b>宇宙刀（宇宙刀走 {@code CosmicFx} 的星空）。 */
    public static boolean isOwnBladeFx(CosmicFxFlag flag) {
        return flag != null && flag.shizuku$isOwnBladeFx();
    }

    // ------------------------------------------------------------------ 刀光（drive）

    /** @return true = 画好了（调用方可以 cancel 上游那一层）；false = 这次没画。 */
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
        if (model == null) {
            return false;
        }
        RenderType renderType = renderType(DRIVE_TEXTURE, DRIVE_TEXTURE_FILE);
        if (renderType == null) {
            return false;
        }
        int color = withAlpha(state.color, (float) baseAlpha);
        final float size = DAOGUANG_SCALE;   // DriveRenderState 不带 baseSize ⇒ 上游固定 1×0.02

        poseStack.pushPose();
        try {
            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, state.yRotO, state.yRot) - 90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.rotLerp(partialTick, state.xRotO, state.xRot)));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.rotationRoll));
            poseStack.scale(size, size, size);
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            submit(poseStack, collector, renderType, model, color, true);
        } finally {
            poseStack.popPose();
        }
        return true;
    }

    // ------------------------------------------------------------------ 斩击（slash effect）

    /** 斩击特效：上游是「追加一层」，我们同样只叠加、不取消上游原层。 */
    public static void submitSlashEffect(SlashEffectRenderState state, PoseStack poseStack,
                                         SubmitNodeCollector collector) {
        if (!(state instanceof CosmicFxFlag flag) || !flag.shizuku$isOwnBladeFx()) {
            return;
        }
        int lifetime = state.lifetime;
        if (lifetime <= 0) {
            return;
        }
        float partialTick = partialTickOf(state);
        float progress = Math.min((float) lifetime, (float) state.tickCount + partialTick) / (float) lifetime;
        double baseAlpha = Math.min(lifetime, (double) Math.max(0.0F, (float) (lifetime - state.tickCount) - partialTick))
                / (double) lifetime;
        baseAlpha = -Math.pow(baseAlpha - 1.0, 4.0) + 1.0;
        if (baseAlpha <= 0.01) {
            return;
        }
        WavefrontObject model = BladeModelManager.getInstance().getModel(SLASH_MODEL);
        if (model == null) {
            return;
        }
        RenderType renderType = renderType(SLASH_TEXTURE, SLASH_TEXTURE_FILE);
        if (renderType == null) {
            return;
        }
        int color = withAlpha(state.color, (float) baseAlpha);

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
            submit(poseStack, collector, renderType, model, color, false);
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 真正的提交。{@code Face} 的全局算子在 <b>lambda 内部</b>设置/还原 —— 这是修 bug 的关键，
     * 26.1 的这个 lambda 要到帧末才执行。
     */
    private static void submit(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType,
                               WavefrontObject model, int color, boolean drive) {
        collector.order(1).submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            Face.alphaOverride = drive ? Face.alphaNoOverride : Face.alphaOverrideYZZ;
            Face.uvOperator = Face.uvDefaultOperator;
            BladeRenderState.setCol(color);
            try {
                PoseStack local = new PoseStack();
                local.last().pose().set(pose.pose());
                local.last().normal().set(pose.normal());
                model.tessellateOnly(consumer, local, MAX_LIGHT, 0, "base");
            } finally {
                Face.alphaOverride = Face.alphaNoOverride;
                Face.uvOperator = Face.uvDefaultOperator;
                BladeRenderState.resetCol();
            }
        });
    }

    // ------------------------------------------------------------------ 渲染类型 / 贴图

    /** 与上游 {@code getSlashBladeBlendColorWrite} 等价（只是换了 pipeline id、不写深度、加法混合）。 */
    private static RenderType renderType(Identifier textureId, Identifier textureFile) {
        if (slashRenderType != null) {
            return slashRenderType;
        }
        if (renderTypeFailed) {
            return null;
        }
        try {
            registerTexture(textureId, textureFile);
            RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                    .withLocation(shizuku.prefix("pipeline/slash_glow"))
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withSampler("Sampler1")
                    .withColorTargetState(new ColorTargetState(java.util.Optional.of(BlendFunction.LIGHTNING),
                            ColorTargetState.WRITE_COLOR))
                    .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.TRIANGLES)
                    // 加法叠层不写深度（同 CosmicShaders 的踩坑结论：26.1 批提交不保序）
                    .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                    .build();
            RenderSetup setup = RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", textureId)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .useLightmap()
                    .useOverlay()
                    .sortOnUpload()
                    .createRenderSetup();
            slashRenderType = RenderType.create("shizuku_slash_glow", setup);
            return slashRenderType;
        } catch (Throwable t) {
            renderTypeFailed = true;
            LOGGER.error("[shizuku] 刀光 RenderType 构建失败，回退为上游原样（可能不可见）", t);
            return null;
        }
    }

    /**
     * 把上游那两张「放错位置」的 PNG 注册进 TextureManager。
     *
     * <p>{@link ReloadableTexture} 会被 TextureManager 记录在 reload 列表里，资源重载（F3+T）后
     * 自动重新调用 {@link TextureContents#load}，所以只需注册一次。</p>
     */
    private static void registerTexture(Identifier id, Identifier file) {
        if (REGISTERED.putIfAbsent(id, Boolean.TRUE) != null) {
            return;
        }
        TextureManagerAccess.register(id, new UpstreamPngTexture(id, file));
    }

    /** 从「真实路径」读 PNG 的贴图（上游的 id 少了 textures/ 段，vanilla 加载不到）。 */
    private static final class UpstreamPngTexture extends ReloadableTexture {
        private final Identifier file;

        private UpstreamPngTexture(Identifier id, Identifier file) {
            super(id);
            this.file = file;
        }

        @Override
        public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
            return TextureContents.load(resourceManager, this.file);
        }
    }

    /** 小包装（避免上面静态方法里到处 try/catch）。 */
    private static final class TextureManagerAccess {
        private static void register(Identifier id, ReloadableTexture texture) {
            try {
                Minecraft.getInstance().getTextureManager().registerAndLoad(id, texture);
            } catch (Throwable t) {
                LOGGER.error("[shizuku] 注册刀光贴图失败 {}", id, t);
            }
        }
    }

    private static float partialTickOf(Object renderState) {
        return ((cn.sh1rocu.slashblade.api.mixin.IEntityRenderStatePartialTick) renderState).sb$partialTick();
    }

    /** 把寿命 alpha 折进顶点色（保留刀本色的 RGB）。 */
    private static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int rgb = color == 0 ? 0xFFFFFF : color;
        return ARGB.color(a, rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
    }
}
