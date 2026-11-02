package com.buwan.shizuku.core;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * 干净公共 API 实现：替代 1.20.1 的反射广播移除实体方案。
 */
public class UltimateEntityRemover {

    public static void remove(Entity entity, Level level) {
        entity.discard();
    }
}
