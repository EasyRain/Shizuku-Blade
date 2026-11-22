package com.buwan.shizuku.mixin;

import com.buwan.shizuku.registry.SAComboRegistry;
import com.buwan.shizuku.sa.Ymsa;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class JudgmentPlayerPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"), require = 0)
    private void shizuku$poseJudgment(LivingEntity player, float limbSwing, float limbAmount, float age, float yaw, float pitch, CallbackInfo ci) {
        player.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            if (!SAComboRegistry.YMSA.getId().equals(state.getComboSeq())) {
                return;
            }
            float time = state.getElapsedTime(player) + Mth.frac(age);
            if (time >= Ymsa.END_TICK) {
                return;
            }

            PlayerModel<?> model = (PlayerModel<?>)(Object)this;
            boolean right = player.getMainArm() == HumanoidArm.RIGHT;
            float side = right ? 1 : -1;
            float brace = shizuku$ease(time / 5);
            float slash = shizuku$ease((time - 6) / (Ymsa.STRIKE_TICK + 1 - 6));
            float lift = shizuku$ease((time - 18) / 13);
            float insert = shizuku$ease((time - 32) / 20);
            float click = shizuku$ease((time - 52) / (Ymsa.SHEATH_TICK - 52));
            float weight = brace * (1 - shizuku$ease((time - Ymsa.SHEATH_TICK) / (Ymsa.END_TICK - Ymsa.SHEATH_TICK)));
            float chest = Mth.lerp(lift, Mth.lerp(slash, -0.58F, 0.76F), -0.25F) * (1 - 0.7F * insert);
            model.body.yRot += side * chest * weight;
            model.body.xRot += (0.17F - 0.12F * lift) * weight;
            model.head.yRot -= side * chest * 0.55F * weight;
            model.head.xRot += (0.10F + 0.12F * insert) * weight;
            ModelPart sword = right ? model.rightArm : model.leftArm;
            ModelPart offhand = right ? model.leftArm : model.rightArm;
            float swordYaw = Mth.lerp(insert, Mth.lerp(lift, Mth.lerp(slash, -0.90F, 1.02F), 0.22F), -0.80F);
            float swordPitch = Mth.lerp(insert, Mth.lerp(lift, Mth.lerp(slash, -0.62F, -1.60F), -1.32F), -0.72F);
            float swordRoll = Mth.lerp(lift, Mth.lerp(slash, -0.47F, 0.82F), -0.36F);
            sword.yRot = Mth.lerp(weight, sword.yRot, side * (swordYaw - 0.12F * click));
            sword.xRot = Mth.lerp(weight, sword.xRot, swordPitch + 0.12F * click);
            sword.zRot = Mth.lerp(weight, sword.zRot, side * swordRoll);
            offhand.xRot = Mth.lerp(weight, offhand.xRot, -0.69F - 0.27F * lift + 0.13F * insert);
            offhand.yRot = Mth.lerp(weight, offhand.yRot, side * (0.55F + 0.17F * insert));
            offhand.zRot = Mth.lerp(weight, offhand.zRot, side * 0.18F);
            model.jacket.copyFrom(model.body);
            model.leftSleeve.copyFrom(model.leftArm);
            model.rightSleeve.copyFrom(model.rightArm);
            model.hat.copyFrom(model.head);
        });
    }

    @Unique
    private static float shizuku$ease(float time) {
        float t = Mth.clamp(time, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
