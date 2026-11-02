package com.buwan.shizuku;

import com.buwan.shizuku.compat.DeferredHolder;
import com.buwan.shizuku.compat.DeferredRegister;
import com.buwan.shizuku.compat.PlayerTickEvent;
import com.buwan.shizuku.compat.ServerTickEvent;
import com.buwan.shizuku.item.MeowStoneItem;
import com.buwan.shizuku.registry.ModBlocks;
import com.buwan.shizuku.registry.ModMenus;
import com.buwan.shizuku.registry.ModRecipeSerializers;
import com.buwan.shizuku.registry.ModRecipeTypes;
import com.buwan.shizuku.registry.ModTileEntities;
import com.buwan.shizuku.sa.GalaxyField;
import com.buwan.shizuku.sa.MoonlightSlash;
import com.buwan.shizuku.sa.SummerSlash;
import com.buwan.shizuku.sa.TeleportSlash;
import com.buwan.shizuku.sa.TimeSlash;
import com.buwan.shizuku.sa.TornadoSlash;
import com.buwan.shizuku.sa.TsubameGaeshi;
import com.buwan.shizuku.sa.UnlimitedBladeWorks;
import com.buwan.shizuku.sa.VoidPrison;
import com.buwan.shizuku.sa.Yingsa;
import com.buwan.shizuku.se.Jingxin;
import com.buwan.shizuku.se.Leise;
import com.buwan.shizuku.se.SERegistry;
import com.buwan.shizuku.se.WorldsFavorite;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

/**
 * Shizuku-Blade Fabric 版主类（1.21.1）。
 * 对应 NeoForge 版 {@code com.buwan.shizuku.shizuku}，注册逻辑改为在 {@link #init()} 中统一执行。
 */
public class shizuku {
    public static final String MODID = "shizuku";

    public static ResourceLocation prefix(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, MODID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MODID);

    /** 喵石（击杀敌对生物 1% 掉落）。 */
    public static final DeferredHolder<Item, Item> MEOW_STONE = ITEMS.register("meow_stone",
            () -> new MeowStoneItem(new Item.Properties()));

    /** 雫工作台物品。 */
    public static final DeferredHolder<Item, Item> SHIZUKU_CRAFTING_TABLE_ITEM = ITEMS.register(
            "shizuku_crafting_table",
            () -> new BlockItem(ModBlocks.SHIZUKU_CRAFTING_TABLE.get(), new Item.Properties()));

    public static final DeferredHolder<SoundEvent, SoundEvent> LEISA_SOUND1 = registerSound("leisa_sound1");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEISA_SOUND2 = registerSound("leisa_sound2");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEISA_SOUND3 = registerSound("leisa_sound3");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLASS_BREAK_SOUND = registerSound("glass_break_sound");

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(prefix(name)));
    }

    /** 由 ShizukuFabric#onInitialize 调用，统一注册（顺序敏感：方块先于物品）。 */
    public static void init() {
        ModBlocks.BLOCKS.registerAll();
        ModTileEntities.BLOCK_ENTITIES.registerAll();
        ModMenus.MENUS.registerAll();
        ModRecipeTypes.RECIPES.registerAll();
        ModRecipeSerializers.SERIALIZERS.registerAll();
        ITEMS.registerAll();
        SOUND_EVENTS.registerAll();

        // 特殊效果（SE）注册
        SERegistry.registerAll();

        // SE 事件（Refabricated 用 Fabric Event：HIT / UPDATE）
        SlashBladeEvent.HIT.register(Jingxin::onHitEntity);
        SlashBladeEvent.UPDATE.register(Leise::onUpdate);
        SlashBladeEvent.HIT.register(com.buwan.shizuku.se.shizuku::onHitEntity);
        SlashBladeEvent.UPDATE.register(com.buwan.shizuku.se.shizuku::onUpdate);
        WorldsFavorite.registerCommon();

        // 剑技（SA）tick 派发：NeoForge PlayerTickEvent/ServerTickEvent → Fabric ServerTickEvents
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerTickEvent.Post serverEvent = new ServerTickEvent.Post();
            GalaxyField.onServerTick(serverEvent);
            Yingsa.onServerTick(serverEvent);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PlayerTickEvent.Post playerEvent = new PlayerTickEvent.Post(player);
                MoonlightSlash.onPlayerTick(playerEvent);
                SummerSlash.onPlayerTick(playerEvent);
                TeleportSlash.onPlayerTick(playerEvent);
                TimeSlash.onPlayerTick(playerEvent);
                TornadoSlash.onPlayerTick(playerEvent);
                TsubameGaeshi.onPlayerTick(playerEvent);
                UnlimitedBladeWorks.onPlayerTick(playerEvent);
                VoidPrison.onPlayerTick(playerEvent);
            }
        });
    }
}
