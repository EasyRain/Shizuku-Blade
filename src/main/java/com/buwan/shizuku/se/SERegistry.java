package com.buwan.shizuku.se;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import static com.buwan.shizuku.shizuku.MODID;

public class SERegistry {
    public static final DeferredRegister<SpecialEffect> REGISTRY_KEY2;
    public static final RegistryObject<SpecialEffect> SHIZUKU;
    // 新增leise SE注册
    public static final RegistryObject<SpecialEffect> LEISE;

    static {
        REGISTRY_KEY2 = DeferredRegister.create(SpecialEffect.REGISTRY_KEY, MODID);
        SHIZUKU = REGISTRY_KEY2.register("shizuku", shizuku::new);
        // 注册leise SE
        LEISE = REGISTRY_KEY2.register("leise", Leise::new);
    }

    public static void register(IEventBus bus){
        REGISTRY_KEY2.register(bus);
    }
}