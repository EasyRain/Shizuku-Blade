package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.shader.CosmicShaders;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityAbstractSummonedSword.class, remap = false)
public abstract class EntityAbstractSummonedSwordMixin {

    @Inject(
            method = "setHitEntity",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void shizuku$disappearOnHit(Entity hitEntity, CallbackInfo ci) {
        if (isMosmicDrive((EntityAbstractSummonedSword) (Object) this)) {
            ci.cancel();
            ((Entity) (Object) this).discard();
        }
    }

    @Inject(
            method = "burst",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void shizuku$silentRemove(CallbackInfo ci) {
        if (isMosmicDrive((EntityAbstractSummonedSword) (Object) this)) {
            ci.cancel();
            ((Entity) (Object) this).discard();
        }
    }

    private static boolean isMosmicDrive(EntityAbstractSummonedSword drive) {
        if (drive.getShooter() instanceof LivingEntity living) {
            ItemStack held = living.getMainHandItem();
            if (!(held.getItem() instanceof ItemSlashBlade)) {
                return false;
            } else {
                String key = CapabilitySlashBlade.getBladeState(held)
                        .map(s -> s.getTranslationKey())
                        .orElse("");
                return CosmicShaders.COSMIC_BLADE_KEY.equals(key);
            }
        } else {
            return false;
        }
    }
}
