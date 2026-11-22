package com.buwan.shizuku.util;

import net.minecraft.nbt.CompoundTag;

/**
 * Forge 的 {@code getPersistentData()} 在 Fabric 上没有对应物。
 * 这个接口由 {@code EntityPersistentDataMixin} / {@code BlockEntityPersistentDataMixin} 注入实现，
 * 提供一份会跟着实体/方块存进存档的附加 NBT。
 */
public interface PersistentDataHolder {
    CompoundTag shizuku$persistentData();
}
