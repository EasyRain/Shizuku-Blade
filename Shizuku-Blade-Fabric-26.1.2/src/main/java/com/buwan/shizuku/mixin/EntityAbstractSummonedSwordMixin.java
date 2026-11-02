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

    /**
     * 命中后静默消失。
     *
     * <p>注意必须用 TAIL 而不是 HEAD+cancel：本体的 {@code onHitBlock} 会写 {@code inBlockState}，
     * 而 {@code tick} 里会直接对它调 {@code equals}（没有空判断）；在 HEAD 取消会让该字段保持 null，
     * 下一次 tick 直接 NPE 崩服。</p>
     */
    @Inject(method = "setHitEntity", at = @At("TAIL"))
    private void shizuku$disappearOnHit(Entity hitEntity, CallbackInfo ci) {
        if (isMosmicDrive((EntityAbstractSummonedSword) (Object) this)) {
            Entity self = (Entity) (Object) this;
            if (self.isAlive()) {
                self.discard();
            }
        }
    }

    @Inject(method = "burst", at = @At("TAIL"))
    private void shizuku$silentRemove(CallbackInfo ci) {
        if (isMosmicDrive((EntityAbstractSummonedSword) (Object) this)) {
            Entity self = (Entity) (Object) this;
            if (self.isAlive()) {
                self.discard();
            }
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
