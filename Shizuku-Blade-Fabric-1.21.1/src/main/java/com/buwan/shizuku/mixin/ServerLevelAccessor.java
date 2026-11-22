package com.buwan.shizuku.mixin;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @Accessor：拿到 ServerLevel 的 entityTickList / navigatingMobs / entityManager，
 * 服务端强制移除实体时用。
 */
@Mixin(ServerLevel.class)
public interface ServerLevelAccessor {
    @Accessor("entityTickList")
    EntityTickList ymsa$getEntityTickList();

    @Accessor("navigatingMobs")
    Set<Mob> ymsa$getNavigatingMobs();

    @Accessor("entityManager")
    PersistentEntitySectionManager<Entity> ymsa$getEntityManager();
}
