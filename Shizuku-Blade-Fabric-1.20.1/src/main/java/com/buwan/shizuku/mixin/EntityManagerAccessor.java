package com.buwan.shizuku.mixin;

import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @Accessor：拿到 PersistentEntitySectionManager 的 knownUuids / visibleEntityStorage /
 * sectionStorage，用于把实体从服务端实体管理器里摘除。
 */
@Mixin(PersistentEntitySectionManager.class)
public interface EntityManagerAccessor {
    @Accessor("knownUuids")
    Set<UUID> ymsa$getKnownUuids();

    @Accessor("visibleEntityStorage")
    EntityLookup<Entity> ymsa$getVisibleEntityStorage();

    @Accessor("sectionStorage")
    EntitySectionStorage<Entity> ymsa$getSectionStorage();
}
