package com.buwan.shizuku.compat;

import net.minecraft.world.entity.Entity;

/**
 * Forge 的 {@code net.minecraftforge.entity.PartEntity} 在 Fabric 上没有对应实现，
 * 这里保留一个同名能力的接口，供审判逻辑用 {@code instanceof} 判断「多部件生物的身体部件」。
 *
 * <p>Forge 侧实现这个接口的是 MutablePartEntity 之类的前置模组实体；Fabric 上通常没有这类模组，
 * 所以判断恒为 false，相关分支不会触发，但代码可以原样保留。</p>
 */
public interface PartEntity {
    Entity getParent();

    boolean isMultipartEntity();

    Entity[] getParts();
}
