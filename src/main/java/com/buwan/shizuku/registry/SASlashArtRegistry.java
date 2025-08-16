package com.buwan.shizuku.registry;

import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import static com.buwan.shizuku.shizuku.MODID;

public class SASlashArtRegistry {
    public static final DeferredRegister<SlashArts> SLASH_ARTS =
            DeferredRegister.create(SlashArts.REGISTRY_KEY, MODID);

    public static final RegistryObject<SlashArts> Shizukusa;
    public static final RegistryObject<SlashArts> Ymsa;

    static {
        Shizukusa = SLASH_ARTS.register("shizukusa", () -> new SlashArts((e) ->
                SAComboRegistry.shizukusa.getId()));
    }

    public static final RegistryObject<SlashArts> Leisa;

    static {
        Leisa = SLASH_ARTS.register("leisa", () -> new SlashArts((e) ->
                SAComboRegistry.LEISA.getId()));
    }

    static {


        Ymsa = SLASH_ARTS.register("ymsa", () -> new SlashArts((e) ->
                SAComboRegistry.YMSA.getId()));
    }
}