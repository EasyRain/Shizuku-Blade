package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.shader.CosmicShaders;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.client.renderer.entity.SlashEffectRenderer;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(SlashEffectRenderer.class)
public abstract class SlashEffectRendererMixin {
    private static final ResourceLocation SLASH_MODEL = ResourceLocation.fromNamespaceAndPath("slashblade", "model/util/slash.obj");

    @Inject(
            method = "render",
            at = @At("TAIL")
    )
    private void shizuku$renderCosmicSlash(
            EntitySlashEffect entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci
    ) {
        if (CosmicShaders.COSMIC_SHADER != null) {
            if (entity.getShooter() instanceof LivingEntity living) {
                ItemStack held = living.getMainHandItem();
                if (held.getItem() instanceof ItemSlashBlade) {
                    String translationKey = BladeStateAccess.of(held)
                            .map(s -> s.getTranslationKey())
                            .orElse("");
                    if (CosmicShaders.COSMIC_BLADE_KEY.equals(translationKey)) {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            int lifetime = entity.getLifetime();
                            if (lifetime > 0) {
                                float progress = Math.min((float) lifetime, entity.tickCount + partialTicks) / (float) lifetime;
                                double deathTime = lifetime;
                                double baseAlpha = Math.min(deathTime, Math.max(0.0F, (lifetime - entity.tickCount) - partialTicks)) / deathTime;
                                baseAlpha = -Math.pow(baseAlpha - 1.0, 4.0) + 1.0;
                                if (!(baseAlpha <= 0.01)) {
                                    WavefrontObject model = BladeModelManager.getInstance().getModel(SLASH_MODEL);
                                    if (model != null) {
                                        poseStack.pushPose();
                                        poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
                                        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.rotLerp(partialTicks, entity.xRotO, entity.getXRot())));
                                        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getRotationRoll()));
                                        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getRotationOffset() - 135.0F * progress));
                                        poseStack.scale(1.0F, 0.25F, 1.0F);
                                        poseStack.scale(1.2F, 1.2F, 1.2F);
                                        float scale = entity.getBaseSize() * Mth.lerp(progress, 0.03F, 0.035F);
                                        poseStack.scale(scale, 0.03F, scale);
                                        CosmicShaders.renderCosmic(model, "base", poseStack, buffer, (float) baseAlpha);
                                        poseStack.popPose();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
