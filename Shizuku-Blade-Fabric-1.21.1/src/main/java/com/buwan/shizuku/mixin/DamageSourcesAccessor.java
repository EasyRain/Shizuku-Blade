package com.buwan.shizuku.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * @Invoker：调用 DamageSources.source(...) 构造自定义伤害来源（夜冥杀的处决伤害）。
 */
@Mixin(DamageSources.class)
public interface DamageSourcesAccessor {
    @Invoker("source")
    DamageSource ymsa$source(ResourceKey<DamageType> var1, @Nullable Entity var2, @Nullable Entity var3);
}
