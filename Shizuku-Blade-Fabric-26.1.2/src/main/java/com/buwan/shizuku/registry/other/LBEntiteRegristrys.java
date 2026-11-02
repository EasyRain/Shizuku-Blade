package com.buwan.shizuku.registry.other;

import com.buwan.shizuku.compat.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

public class LBEntiteRegristrys {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, "shizuku");

    public static void register(Object eventBus) {
        ENTITIES.register(eventBus);
    }
}
