package com.buwan.shizuku.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @Accessor：拿到 ClientLevel 的 tickingEntities / entityStorage（partEntities 是 Forge 独有，Fabric 版没有），
 * 客户端侧强制移除实体时用。
 */
@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
    @Accessor("tickingEntities")
    EntityTickList ymsa$getTickingEntities();

    @Accessor("entityStorage")
    TransientEntitySectionManager<Entity> ymsa$getEntityStorage();
}
