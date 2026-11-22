package com.buwan.shizuku.mixin;

import com.buwan.shizuku.sa.Ymsa;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在实体掉落处理完成之后通知夜冥杀的清理流程（记录「已经掉过战利品」）。
 */
@Mixin(LivingEntity.class)
public class LivingEntityDropMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("RETURN"), require = 0)
    private void ymsa$dropsProcessed(DamageSource source, CallbackInfo ci) {
        Ymsa.handleDropsProcessed((LivingEntity)(Object)this);
    }
}
