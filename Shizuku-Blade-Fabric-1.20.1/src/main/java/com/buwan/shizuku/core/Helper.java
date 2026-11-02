package com.buwan.shizuku.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 干净公共 API 实现：仅保留 SA 实际用到的实体枚举工具。
 */
public class Helper {

    /** 收集当前维度全部实体（仅服务器端；1.21.1 中 getAllEntities 仅存在于 ServerLevel）。 */
    public static List<Entity> getAllEntities(Level level) {
        List<Entity> entities = new ArrayList<>();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getAllEntities().forEach(entities::add);
        }
        return entities;
    }
}
