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

    /** 发射者是否手持宇宙刀（= 原版 {@code isMosmicDrive} / 斩击里那段 translationKey 判定）。 */
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
    public static void submitDrive(DriveRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        if (!(state instanceof CosmicFxFlag flag) || !flag.shizuku$isCosmicFx()) {
            return;
        }
        float lifetime = state.lifetime;
        if (lifetime <= 0.0F) {
            return;
        }
        float partialTick = partialTickOf(state);

        double deathTime = lifetime;
        double baseAlpha = Math.min(deathTime, (double) Math.max(0.0F, lifetime - (float) state.tickCount)) / deathTime;
        baseAlpha = Math.max(0.0, -Math.pow(baseAlpha - 1.0, 4.0) + 0.75);
        if (baseAlpha <= 0.01) {
            return;
        }
        WavefrontObject model = BladeModelManager.getInstance().getModel(DAOGUANG_MODEL);
        if (model == null) {
            return;
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
    }
}
