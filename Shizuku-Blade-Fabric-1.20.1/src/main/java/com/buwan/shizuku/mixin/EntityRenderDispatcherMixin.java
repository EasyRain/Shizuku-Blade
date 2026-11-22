package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.YmsaClientErase;
import com.buwan.shizuku.client.YmsaRenderKiller;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在实体渲染分发处跳过「已被夜冥杀标记为抹除」的实体，避免它们再被画一帧。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void ymsa$skipErasedRender(
        Entity entity,
        double x,
        double y,
        double z,
        float rotYaw,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        CallbackInfo ci
    ) {
        YmsaRenderKiller.reassert((EntityRenderDispatcher)(Object)this);
        if (YmsaClientErase.isErased(entity)) {
            ci.cancel();
        }
    }
}
