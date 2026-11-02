package com.buwan.shizuku.se;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.buwan.shizuku.shizuku.MODID;

public class SERegistry {
    public static final DeferredRegister<SpecialEffect> REGISTRY_KEY2;
    public static final DeferredHolder<SpecialEffect, SpecialEffect> SHIZUKU;
    public static final DeferredHolder<SpecialEffect, SpecialEffect> LEISE;
    public static final DeferredHolder<SpecialEffect, SpecialEffect> JINGXIN;
    public static final DeferredHolder<SpecialEffect, SpecialEffect> WORLDS_FAVORITE;

    static {
        REGISTRY_KEY2 = DeferredRegister.create(SpecialEffect.REGISTRY_KEY, MODID);
        SHIZUKU = REGISTRY_KEY2.register("shizuku", shizuku::new);
        LEISE = REGISTRY_KEY2.register("leise", Leise::new);
        JINGXIN = REGISTRY_KEY2.register("jingxin", Jingxin::new);
        WORLDS_FAVORITE = REGISTRY_KEY2.register("worlds_favorite", WorldsFavorite::new);
    }

    public static void register(IEventBus bus) {
        REGISTRY_KEY2.register(bus);
    }
}
