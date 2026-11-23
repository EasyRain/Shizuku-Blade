package com.buwan.shizuku.mixin;

import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取弹射物的发射者 UUID，用于「审判期间把残留弹射物一起清掉」。
 *
 * <p>26.1 起 {@code Projectile} 不再直接存 {@code ownerUUID}，而是存
 * {@link EntityReference}（内部是「UUID 或实体实例」的二选一）。
 * 所以这里暴露 {@code owner} 字段本身，再取 {@link EntityReference#getUUID()}。</p>
 */
@Mixin(Projectile.class)
public interface JudgmentProjectileAccessor {
    @Accessor("owner")
    @Nullable
    EntityReference<Entity> shizuku$ownerRef();

}
