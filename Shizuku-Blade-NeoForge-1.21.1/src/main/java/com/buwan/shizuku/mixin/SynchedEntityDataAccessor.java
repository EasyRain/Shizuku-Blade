package com.buwan.shizuku.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.DataItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @Accessor：拿到 SynchedEntityData.itemsById（同步数据表），
 * 「血量毁灭者」直接改里面的血量条目。
 */
@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataAccessor {
    @Accessor("itemsById")
    Int2ObjectMap<DataItem<?>> ymsa$getItemsById();
}
