package com.buwan.shizuku.core;

import net.minecraft.world.entity.Entity;

/**
 * 干净公共 API 实现：空实现。实体的移除状态由原版 isRemoved() 维护。
 */
public class IsRemovedHelper {

    public static void setIsRemoved(Entity entity, boolean isRemoved) {
        // no-op
    }
}
