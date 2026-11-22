package com.buwan.shizuku.mixin;

import com.buwan.shizuku.util.PersistentDataHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 给每个实体加一份 shizuku 专用附加 NBT（等价 Forge 的 getPersistentData），并随实体存档读写。 */
@Mixin(Entity.class)
public abstract class EntityPersistentDataMixin implements PersistentDataHolder {
    @Unique
    private final CompoundTag shizuku$data = new CompoundTag();

    @Override
    public CompoundTag shizuku$persistentData() {
        return shizuku$data;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"), require = 0)
    private void shizuku$saveData(CompoundTag tag, CallbackInfo ci) {
        if (!shizuku$data.isEmpty()) {
            tag.put("shizuku:data", shizuku$data);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"), require = 0)
    private void shizuku$readData(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("shizuku:data")) {
            CompoundTag saved = tag.getCompound("shizuku:data");
            for (String key : saved.getAllKeys()) {
                shizuku$data.put(key, saved.get(key));
            }
        }
    }
}
