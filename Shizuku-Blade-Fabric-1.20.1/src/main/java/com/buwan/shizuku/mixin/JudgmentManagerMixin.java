package com.buwan.shizuku.mixin;

import com.buwan.shizuku.sa.Ymsa;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PersistentEntitySectionManager.class)
public abstract class JudgmentManagerMixin {
    // Forge funnels normal adds, event-free adds and disk loads through this final insertion.
    @Inject(method = "addEntityWithoutEvent", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void shizuku$checkVerdict(EntityAccess value, boolean fromDisk, CallbackInfoReturnable<Boolean> result) {
        if (value instanceof Entity entity && Ymsa.blocksUnleashedJoin(entity)) result.setReturnValue(false);
    }
}
