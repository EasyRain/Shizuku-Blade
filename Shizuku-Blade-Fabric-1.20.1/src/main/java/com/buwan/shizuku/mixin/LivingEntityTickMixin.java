package com.buwan.shizuku.mixin;

import com.buwan.shizuku.sa.Ymsa;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截「已被夜冥杀抹除」的实体的 tick，让它们静止不再行动，
 * 直到被真正移除（避免抹除瞬间还在攻击玩家）。
 */
@Mixin(LivingEntity.class)
public class LivingEntityTickMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void ymsa$suppressAnnihilatedTick(CallbackInfo ci) {
        if (Ymsa.isTickSuppressed((LivingEntity)(Object)this)) {
            ci.cancel();
        }
    }
}
