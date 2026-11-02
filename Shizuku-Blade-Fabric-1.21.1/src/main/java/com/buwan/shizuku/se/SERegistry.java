package com.buwan.shizuku.se;

import com.buwan.shizuku.compat.DeferredHolder;
import com.buwan.shizuku.compat.DeferredRegister;
import mods.flammpfeil.slashblade.registry.SpecialEffectsRegistry;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

import static com.buwan.shizuku.shizuku.MODID;

public class SERegistry {
    public static final DeferredRegister<SpecialEffect> REGISTRY_KEY2 =
            DeferredRegister.create(SpecialEffectsRegistry.SPECIAL_EFFECT, MODID);

    public static final DeferredHolder<SpecialEffect, SpecialEffect> SHIZUKU = REGISTRY_KEY2.register("shizuku", shizuku::new);
    public static final DeferredHolder<SpecialEffect, SpecialEffect> LEISE = REGISTRY_KEY2.register("leise", Leise::new);
    public static final DeferredHolder<SpecialEffect, SpecialEffect> JINGXIN = REGISTRY_KEY2.register("jingxin", Jingxin::new);
    public static final DeferredHolder<SpecialEffect, SpecialEffect> WORLDS_FAVORITE = REGISTRY_KEY2.register("worlds_favorite", WorldsFavorite::new);

    public static void registerAll() {
        REGISTRY_KEY2.registerAll();
    }
}
