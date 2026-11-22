package com.buwan.shizuku.mixin;

import com.buwan.shizuku.sa.Ymsa;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 ServerLevel.addEntity 处拦截：被标记为「已抹除」的实体不再加入世界。
 */
@Mixin(ServerLevel.class)
public class ServerAddEntityMixin {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void ymsa$blockSuppressedJoin(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Ymsa.shouldCancelJoin(entity)) {
            cir.setReturnValue(false);
        }
    }
}
