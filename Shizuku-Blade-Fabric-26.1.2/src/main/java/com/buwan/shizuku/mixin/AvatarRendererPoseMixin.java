package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.JudgmentPoseFlag;
import com.buwan.shizuku.registry.SAComboRegistry;
import com.buwan.shizuku.sa.Ymsa;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 采集审判姿势参数（26.1）。
 *
 * <p>1.21.1 及更早的 {@code JudgmentPlayerPoseMixin} 直接注入
 * {@code PlayerModel#setupAnim(LivingEntity, …)}；26.1 的该方法只接收渲染状态快照，
 * 实体不可达。这里在 {@code AvatarRenderer#extractRenderState}（实体仍在）里，
 * 判断「主手刀的连招 = YMSA 且审判未结束」，把已过 tick 与主手写进渲染状态。</p>
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererPoseMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;"
                    + "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void shizuku$collectJudgmentPose(Avatar entity, AvatarRenderState state, float partialTick,
                                             CallbackInfo ci) {
        if (!(state instanceof JudgmentPoseFlag flag)) {
            return;
        }
        float time = -1.0F;
        if (entity != null) {
            var blade = entity.getMainHandItem();
            var bladeState = CapabilitySlashBlade.getBladeState(blade).orElse(null);
            if (bladeState != null && SAComboRegistry.YMSA.getId().equals(bladeState.getComboSeq())) {
                float elapsed = bladeState.getElapsedTime(entity) + Mth.frac(state.ageInTicks);
                if (elapsed >= 0.0F && elapsed < Ymsa.END_TICK) {
                    time = elapsed;
                }
            }
        }
        flag.shizuku$setJudgmentPose(time, entity != null && entity.getMainArm() == HumanoidArm.RIGHT);
    }
}
