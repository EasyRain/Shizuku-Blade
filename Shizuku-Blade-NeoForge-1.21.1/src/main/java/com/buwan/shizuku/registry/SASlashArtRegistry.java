package com.buwan.shizuku.registry;

import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.buwan.shizuku.shizuku.MODID;

public class SASlashArtRegistry {

    public static final DeferredRegister<SlashArts> SLASH_ARTS =
            DeferredRegister.create(SlashArts.REGISTRY_KEY, MODID);

    public static final DeferredHolder<SlashArts, SlashArts> Shizukusa =
            SLASH_ARTS.register("shizukusa", () -> new SlashArts(e -> SAComboRegistry.shizukusa.getId()));

    public static final DeferredHolder<SlashArts, SlashArts> Leisa =
            SLASH_ARTS.register("leisa", () -> new SlashArts(e -> SAComboRegistry.LEISA.getId()));

    public static final DeferredHolder<SlashArts, SlashArts> Yingsa =
            SLASH_ARTS.register("yingsa", () -> new SlashArts(e -> SAComboRegistry.YINGSA.getId()));

    public static final DeferredHolder<SlashArts, SlashArts> TeleportSlash =
            SLASH_ARTS.register("teleport_slash", () -> new SlashArts(e -> SAComboRegistry.TELEPORT_SLASH.getId()));

    public static final DeferredHolder<SlashArts, SlashArts> TimeSlash =
            SLASH_ARTS.register("time_slash", () -> new SlashArts(e -> SAComboRegistry.TIME_SLASH.getId()));

    public static final DeferredHolder<SlashArts, SlashArts> TornadoSlash =
            SLASH_ARTS.register("tornado_slash", () -> new SlashArts(e -> SAComboRegistry.TORNADO_SLASH.getId()));

    public static final DeferredHolder<SlashArts, SlashArts> UnlimitedBladeWorks =
            SLASH_ARTS.register("unlimited_blade_works", () -> new SlashArts(e -> SAComboRegistry.UNLIMITED_BLADE_WORKS.getId()));

    public static final DeferredHolder<SlashArts, SlashArts> MoonlightSlash =
            SLASH_ARTS.register("moonlight_slash", () -> new SlashArts(e -> SAComboRegistry.MOONLIGHT_SLASH.getId()));

    public static final DeferredHolder<SlashArts, SlashArts> TsubameGaeshi =
            SLASH_ARTS.register("tsubame_gaeshi", () -> new SlashArts(e -> SAComboRegistry.TSUBAME_GAESHI.getId()));

    public static final DeferredHolder<SlashArts, SlashArts> VoidPrison =
            SLASH_ARTS.register("void_prison", () -> new SlashArts(e -> SAComboRegistry.VOID_PRISON.getId()));

    public static final DeferredHolder<SlashArts, SlashArts> CosmicCrossSlash =
            SLASH_ARTS.register("cosmic_cross_slash", () -> new SlashArts(e -> SAComboRegistry.COSMIC_CROSS_SLASH.getId()));

    public static final DeferredHolder<SlashArts, SlashArts> Ymsa =
            SLASH_ARTS.register("ymsa", () -> new SlashArts(e -> SAComboRegistry.YMSA.getId()));

    public static final DeferredHolder<SlashArts, SlashArts> SummerSlash =
            SLASH_ARTS.register("summer_slash", () -> new SlashArts(e -> SAComboRegistry.SUMMER_SLASH.getId()));

    // 3.6.0：星河斩（免耀魂消耗）
    public static final DeferredHolder<SlashArts, SlashArts> GalaxyArc =
            SLASH_ARTS.register("galaxy_arc", () -> new SlashArts(e -> SAComboRegistry.GALAXY_ARC.getId()).setProudSoulCost(0));
}
