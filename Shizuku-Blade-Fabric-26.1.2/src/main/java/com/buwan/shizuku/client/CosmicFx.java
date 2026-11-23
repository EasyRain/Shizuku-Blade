package com.buwan.shizuku.client;

import cn.sh1rocu.slashblade.api.mixin.IEntityRenderStatePartialTick;
import com.buwan.shizuku.client.shader.CosmicShaders;
import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.client.renderer.entity.state.DriveRenderState;
import mods.flammpfeil.slashblade.client.renderer.entity.state.SlashEffectRenderState;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 宇宙刀的「斩击 / 刀光星光」（26.1，1.20.1 原版忠实移植）。
 *
 * <p>1.20.1 原版（modrinth37）两处入口 {@code SlashEffectRendererMixin} /
 * {@code DriveRendererMixin}，都调同一个
 * {@code CosmicShaders.renderCosmic(model, "base", poseStack, buffer, baseAlpha)}：</p>
 * <ul>
 *   <li><b>斩击（斩击特效实体）</b>：TAIL 追加一层，<b>不取消</b>原效果 → 与手持刀身同样是
 *       「原效果 + 星空叠加」；模型 = 上游 {@code slashblade:model/util/slash.obj} 的
 *       {@code base} 组（288 面），姿态链照抄 vanilla（含 {@code rotationOffset-135*progress}
 *       与 {@code scale(1,0.25,1)*1.2}），opacity = {@code -pow(a-1,4)+1}；</li>
 *   <li><b>刀光（drive）</b>：HEAD <b>取消</b>原发光层，改画自带的
 *       {@code shizuku:model/util/daoguang.obj}（{@code base} 组 12 面），
 *       缩放 {@code 0.02*max(1,baseSize)}，opacity = {@code max(0, -pow(a-1,4)+0.75)}。</li>
 * </ul>
 *
 * <p>26.1 三段差异（只有通道，数学逐行一致）：① 判定在 extract（有 entity）、绘制在
 * submit（只有 render state）⇒ 标记走 {@link CosmicFxFlag}；② 参数从 per-draw uniform
 * 换成顶点属性（见 {@link CosmicShaders#submitModel}）；③
 * {@code submitNodeCollector.submitCustomGeometry} 代替 {@code MultiBufferSource}。</p>
 */
public final class CosmicFx {

    /** 上游斩击模型（原版写死同一个 id）。 */
    public static final Identifier SLASH_MODEL = Identifier.fromNamespaceAndPath("slashblade", "model/util/slash.obj");
    /** 我们的刀光模型（原版纯色刀光用上游 drive.obj，宇宙刀换成这个，故随本模组走）。 */
    public static final Identifier DAOGUANG_MODEL = shizuku.prefix("model/util/daoguang.obj");
    /** 刀光缩放（原版常量 0.02）。 */
    private static final float DAOGUANG_SCALE = 0.02F;

    private CosmicFx() {
    }

    /**
     * 本模组的刀（宇宙刀 + 其余 shizuku 命名刀）统一前缀。
     *
     * <p>拔刀剑的「刀」不是独立的 Item 类，而是同一个 {@code ItemSlashBlade} 上带不同
     * translationKey（{@code item.shizuku.xxx} / 上游是 {@code item.slashblade.xxx}），
     * 所以「是不是我们自己的刀」只能按 key 前缀判。</p>
     */
    private static final String SHIZUKU_BLADE_PREFIX = "item.shizuku.";

    /** 该刀翻译键是否属于本模组（宇宙刀也算在内）。 */
    public static boolean isShizukuBladeKey(String key) {
        return key != null && key.startsWith(SHIZUKU_BLADE_PREFIX);
    }

    /**
     * 发射者是否手持<b>我们自己的刀</b>（原 {@code isCosmicShooter} 的放宽版）。
     *
     * <p>2026-11-23 起斩击/刀光的接管范围从「只有宇宙刀」放宽到「所有 shizuku 刀」：
     * 上游 26.1 的 {@code .obj} 刀光在我们这边几乎不可见（用户实测「放 SA 什么都没有」），
     * 而 {@link CosmicShaders} 这条管线本来就能正常出图，所以让雫草/雷萨/燕返/瞬影/银影/
     * 稻光/甘城… 也走同一条已经验证过的几何。</p>
     *
     * <p><b>边界</b>：上游的刀（{@code item.slashblade.*}）与任何非拔刀剑物品都返回 false，
     * 上游自己的刀光维持原样，我们只接管自己的刀 —— 不替上游兜底、也不无条件 cancel。</p>
     *
     * <p>配色：这套管线采样的是星空贴图（原版宇宙刀参数），所以现阶段所有自己的刀都是
     * 「星空刀光」。要区分普通刀光配色，得再开一条纯色管线（后续美化工作）。</p>
     */
    public static boolean isShizukuShooter(Entity shooter) {
        if (!(shooter instanceof LivingEntity living)) {
            return false;
        }
        ItemStack held = living.getMainHandItem();
        if (!(held.getItem() instanceof ItemSlashBlade)) {
            return false;
        }
        String key = CapabilitySlashBlade.getBladeState(held)
                .map(s -> s.getTranslationKey())
                .orElse("");
        return isShizukuBladeKey(key);
    }

    /**
     * A/B 开关：{@code -Dshizuku.slashfx.use_cosmic=true}。
     *
     * <p>打开时，「我们自己的刀（非宇宙刀）」也把渲染<b>委托给 CosmicFx 这条已经实测能出画面的
     * 代码路径</b>（模型/贴图/顶点写法/管线全用它），只用于判定「到底是我们的实现不对，
     * 还是接线/门控不对」。诊断开关，平时不要开。</p>
     */
    public static boolean useCosmicPathForOwnBlades() {
        return Boolean.getBoolean("shizuku.slashfx.use_cosmic");
    }

    /**
     * 发射者手持的是<b>我们自己的刀、但不是宇宙刀</b> —— 这类走 {@code UpstreamSlashFx}
     * 的普通刀光（照抄上游画法），星空管线只留给宇宙刀。
     */
    public static boolean isOwnBladeShooter(Entity shooter) {
        return isShizukuShooter(shooter) && !isCosmicShooter(shooter);
    }

    /** 发射者是否手持宇宙刀（星空刀光专用；保持 1.20.1 原版语义）。 */
    public static boolean isCosmicShooter(Entity shooter) {
        if (!(shooter instanceof LivingEntity living)) {
            return false;
        }
        ItemStack held = living.getMainHandItem();
        if (!(held.getItem() instanceof ItemSlashBlade)) {
            return false;
        }
        String key = CapabilitySlashBlade.getBladeState(held)
                .map(s -> s.getTranslationKey())
                .orElse("");
        return CosmicShaders.COSMIC_BLADE_KEY.equals(key);
    }

    private static float partialTickOf(Object renderState) {
        return ((IEntityRenderStatePartialTick) renderState).sb$partialTick();
    }

    /**
     * 斩击特效的星空叠加层（原版 {@code SlashEffectRendererMixin} @ TAIL，不取消）。
     *
     * <p>姿态链与 vanilla {@code submit} 里最后那层完全一致（原版是在 TAIL 重放一遍，
     * 因为 vanilla 已经把 poseStack 弹回入口状态）。</p>
     */
    public static void submitSlashEffect(SlashEffectRenderState state, PoseStack poseStack,
                                         SubmitNodeCollector collector) {
        if (!(state instanceof CosmicFxFlag flag) || !flag.shizuku$isCosmicFx()) {
            return;
        }
        int lifetime = state.lifetime;
        if (lifetime <= 0) {
            return;
        }
        float partialTick = partialTickOf(state);
        float progress = Math.min((float) lifetime, (float) state.tickCount + partialTick) / (float) lifetime;

        // 原版：min(deathTime, max(0, lifetime - tickCount - partialTick)) / deathTime，
        // 再 -pow(a-1, 4) + 1.0（淡入淡出曲线）
        double deathTime = lifetime;
        double baseAlpha = Math.min(deathTime,
                Math.max(0.0F, (float) (lifetime - state.tickCount) - partialTick)) / deathTime;
        baseAlpha = -Math.pow(baseAlpha - 1.0, 4.0) + 1.0;
        if (baseAlpha <= 0.01) {
            return;
        }
        WavefrontObject model = BladeModelManager.getInstance().getModel(SLASH_MODEL);
        if (model == null) {
            return;
        }

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
            CosmicShaders.submitModel(model, "base", poseStack, collector, (float) baseAlpha);
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 刀光的星空层（原版 {@code DriveRendererMixin} @ HEAD + cancel：整层替换成 daoguang.obj）。
     *
     * <p>注意原版刀光<b>不用</b> partialTick 算 alpha（只有 rotLerp 用），这里保持一致。</p>
     */
    /**
     * @return true = 已经画好（调用方可以安全地 cancel 上游那一层）；
     *         false = 这次没画（标记不匹配 / 淡出结束 / 模型缺失），<b>不要</b> cancel。
     */
    public static boolean submitDrive(DriveRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        if (!(state instanceof CosmicFxFlag flag) || !flag.shizuku$isCosmicFx()) {
            return false;
        }
        float lifetime = state.lifetime;
        if (lifetime <= 0.0F) {
            return false;
        }
        float partialTick = partialTickOf(state);

        double deathTime = lifetime;
        double baseAlpha = Math.min(deathTime, (double) Math.max(0.0F, lifetime - (float) state.tickCount)) / deathTime;
        baseAlpha = Math.max(0.0, -Math.pow(baseAlpha - 1.0, 4.0) + 0.75);
        if (baseAlpha <= 0.01) {
            return false;
        }
        WavefrontObject model = BladeModelManager.getInstance().getModel(DAOGUANG_MODEL);
        if (model == null) {
            return false;
        }

        poseStack.pushPose();
        try {
            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, state.yRotO, state.yRot) - 90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.rotLerp(partialTick, state.xRotO, state.xRot)));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.rotationRoll));
            float size = Math.max(1.0F, flag.shizuku$getBaseSize()) * DAOGUANG_SCALE;
            poseStack.scale(size, size, size);
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            CosmicShaders.submitModel(model, "base", poseStack, collector, (float) baseAlpha);
        } finally {
            poseStack.popPose();
        }
        return true;
    }
}
