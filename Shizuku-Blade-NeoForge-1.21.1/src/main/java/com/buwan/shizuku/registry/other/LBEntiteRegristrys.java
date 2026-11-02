package com.buwan.shizuku.registry.other;

import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

// 导入必要的类


public class LBEntiteRegristrys {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, "shizuku");


    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
