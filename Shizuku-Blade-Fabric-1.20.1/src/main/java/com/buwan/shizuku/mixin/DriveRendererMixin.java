package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.shader.CosmicShaders;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.client.renderer.entity.DriveRenderer;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(value = DriveRenderer.class, remap = false)
public abstract class DriveRendererMixin {
    private static final ResourceLocation DAOGUANG_MODEL = new ResourceLocation("shizuku", "model/util/daoguang.obj");
    private static final float DAOGUANG_SCALE = 0.02F;

    @Inject(
            method = "render",
            at = @At("HEAD"),
            cancellable = true,
            // 可选注入：前置改签名时只是特效失效，不崩启动（详见 EntityAbstractSummonedSwordMixin 的说明）
            require = 0
    )
    private void shizuku$renderDaoguang(
            EntityDrive entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci
    ) {
        if (isMosmicDrive(entity)) {
            ci.cancel();
            if (CosmicShaders.COSMIC_SHADER != null) {
                float lifetime = entity.getLifetime();
                if (!(lifetime <= 0.0F)) {
                    double deathTime = lifetime;
                    double baseAlpha = Math.min(deathTime, Math.max(0.0F, lifetime - entity.tickCount)) / deathTime;
                    baseAlpha = Math.max(0.0, -Math.pow(baseAlpha - 1.0, 4.0) + 0.75);
                    if (!(baseAlpha <= 0.01)) {
                        WavefrontObject model = BladeModelManager.getInstance().getModel(DAOGUANG_MODEL);
                        if (model != null) {
                            poseStack.pushPose();
                            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
                            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot())));
                            poseStack.mulPose(Axis.XP.rotationDegrees(entity.getRotationRoll()));
                            float size = Math.max(1.0F, entity.getBaseSize());
                            poseStack.scale(0.02F * size, 0.02F * size, 0.02F * size);
                            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                            CosmicShaders.renderCosmic(model, "base", poseStack, buffer, (float) baseAlpha);
                            poseStack.popPose();
                        }
                    }
                }
            }
        }
    }

    private static boolean isMosmicDrive(EntityDrive entity) {
        if (entity.getShooter() instanceof LivingEntity living) {
            ItemStack held = living.getMainHandItem();
            return held.getItem() instanceof ItemSlashBlade
                    && CapabilitySlashBlade.BLADESTATE.maybeGet(held)
                    .map(s -> CosmicShaders.COSMIC_BLADE_KEY.equals(s.getTranslationKey()))
                    .orElse(false);
        } else {
            return false;
        }
    }
}
