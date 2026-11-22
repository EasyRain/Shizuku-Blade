package com.buwan.shizuku.mixin;

import com.buwan.shizuku.util.PersistentDataHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 方块实体版本：同样提供会写进存档的附加 NBT。 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityPersistentDataMixin implements PersistentDataHolder {
    @Unique
    private final CompoundTag shizuku$data = new CompoundTag();

    @Override
    public CompoundTag shizuku$persistentData() {
        return shizuku$data;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 0)
    private void shizuku$saveData(CompoundTag tag, CallbackInfo ci) {
        if (!shizuku$data.isEmpty()) {
            tag.put("shizuku:data", shizuku$data);
        }
    }

    @Inject(method = "load", at = @At("TAIL"), require = 0)
    private void shizuku$readData(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("shizuku:data")) {
            CompoundTag saved = tag.getCompound("shizuku:data");
            for (String key : saved.getAllKeys()) {
                shizuku$data.put(key, saved.get(key));
            }
        }
    }
}
