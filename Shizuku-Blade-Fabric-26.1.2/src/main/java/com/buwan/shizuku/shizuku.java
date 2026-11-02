package com.buwan.shizuku;

import com.buwan.shizuku.compat.DeferredHolder;
import com.buwan.shizuku.compat.DeferredRegister;
import com.buwan.shizuku.compat.PlayerTickEvent;
import com.buwan.shizuku.compat.ServerTickEvent;
import com.buwan.shizuku.item.MeowStoneItem;
import com.buwan.shizuku.network.PacketHandler;
import com.buwan.shizuku.registry.ModBlocks;
import com.buwan.shizuku.registry.ModMenus;
import com.buwan.shizuku.registry.ModCreativeTabs;
import com.buwan.shizuku.registry.ModRecipeSerializers;
import com.buwan.shizuku.registry.ModRecipeTypes;
import com.buwan.shizuku.registry.ModTileEntities;
import com.buwan.shizuku.registry.SAComboRegistry;
import com.buwan.shizuku.registry.SASlashArtRegistry;
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
import com.buwan.shizuku.sa.Ymsa;
import com.buwan.shizuku.se.Jingxin;
import com.buwan.shizuku.se.Leise;
import com.buwan.shizuku.se.SERegistry;
import com.buwan.shizuku.se.WorldsFavorite;
import com.buwan.shizuku.server.BladeProtectorServer;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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

    public static Identifier prefix(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
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
        ModRecipeSerializers.registerAll();
        ITEMS.registerAll();
        SOUND_EVENTS.registerAll();

        // 「猫羽雫」创造模式标签页
        ModCreativeTabs.register();

        // 特殊效果（SE）注册
        SERegistry.registerAll();

        // 剑技注册表（自定义 Registry：SlashArts / ComboState）
        SASlashArtRegistry.SLASH_ARTS.registerAll();
        SAComboRegistry.COMBO_STATES.registerAll();

        // 抹除（Ymsa）系统事件
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (world instanceof ServerLevel) {
                Ymsa.onEntityJoin(entity);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                Ymsa.onPlayerLoggedOut(handler.getPlayer()));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
                Ymsa.onPlayerChangedDimension(player));

        // 保护刀（登录/死亡时补保护标签）
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                BladeProtectorServer.onPlayerLoggedIn(handler.getPlayer()));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) ->
                BladeProtectorServer.onPlayerDeath(entity));

        // 网络 payload 注册 + 服务器实例捕获（sendToAll 用）
        PacketHandler.register();
        ServerLifecycleEvents.SERVER_STARTED.register(PacketHandler::setServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> PacketHandler.setServer(null));

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
            Ymsa.onServerTick(server);
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
