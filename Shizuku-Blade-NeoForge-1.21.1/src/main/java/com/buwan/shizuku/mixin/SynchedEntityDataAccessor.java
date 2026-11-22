package com.buwan.shizuku.mixin;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.DataItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @Accessor：拿到 SynchedEntityData.itemsById（同步数据表）。
 *
 * <p>1.21 起这个字段从 {@code Int2ObjectMap} 变成了 {@code DataItem<?>[]}，
 * 访问器类型要跟着改（目前没有调用方，保留备用）。</p>
 */
@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataAccessor {
    @Accessor("itemsById")
    DataItem<?>[] ymsa$getItemsById();
}
