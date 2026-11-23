package com.buwan.shizuku.compat;

import java.util.Collection;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Forge 的 {@code Level#getPartEntities()} 在 Fabric 上不存在（多部件实体是 Forge 的 MutablePartEntity 体系）。
 *
 * <p>Fabric 侧恒返回空集合；调用方保留遍历逻辑，将来若接了等价前置再替换实现。</p>
 */
public final class Multiparts {
    private Multiparts() {}

    public static Collection<Entity> of(Level level) {
        return List.of();
    }
}
