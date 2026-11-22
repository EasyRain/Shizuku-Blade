package com.buwan.shizuku.mixin;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Projectile.class)
public interface JudgmentProjectileAccessor {
    @Accessor("ownerUUID") @Nullable UUID shizuku$ownerUUID();
}
