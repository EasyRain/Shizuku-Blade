package com.buwan.shizuku.core;

import net.minecraft.world.entity.Entity;

/**
 * 干净公共 API 实现：空实现。1.21.1 的移除原因由原版 discard()/kill() 自行维护，无需外部覆写。
 */
public class RemovalReasonHelper {

    public static void setRemovalReason(Entity entity, Entity.RemovalReason removalReason) {
        // no-op
    }
}
