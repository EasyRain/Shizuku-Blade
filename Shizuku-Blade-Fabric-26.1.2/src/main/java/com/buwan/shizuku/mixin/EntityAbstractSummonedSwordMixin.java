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
     * 修补前置 mod 的空指针 bug：{@code inBlockState} 只在 {@code onHitBlock} 里赋值，
     * 但本体的 {@code tick} 会直接对它调用 {@code equals}（没有判空）。
     *
     * <p>后果：任何"还没撞过方块"就被存档/加载的刀光实体，下一次 tick 必定 NPE 崩服
     * （实测禁用本模组后依然复现，属上游问题）。这里在每次 tick 前补一个合法值兜底。</p>
     */
    @org.spongepowered.asm.mixin.Shadow
    private net.minecraft.world.level.block.state.BlockState inBlockState;

    @Inject(method = "tick", at = @At("HEAD"))
    private void shizuku$fixNullInBlockState(CallbackInfo ci) {
        if (this.inBlockState == null) {
            Entity self = (Entity) (Object) this;
            if (self.level() != null) {
                this.inBlockState = self.level().getBlockState(self.blockPosition());
            }
        }
    }

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
