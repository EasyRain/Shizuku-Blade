package com.buwan.shizuku.core;

import net.minecraft.world.entity.Entity;

/**
 * 干净公共 API 实现：空实现。实体的“已加入世界”状态由原版 isAddedToWorld() 维护。
 */
public class AddedToWorldHelper {

    public static void setAddedToWorldValue(Entity entity, boolean value) {
        // no-op
    }
}
