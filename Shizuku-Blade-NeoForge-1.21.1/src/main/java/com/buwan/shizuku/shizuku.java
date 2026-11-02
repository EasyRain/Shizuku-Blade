package com.buwan.shizuku;


import com.buwan.shizuku.client.CosmicBladeRenderer;
import com.buwan.shizuku.client.FieldActivateInput;
import com.buwan.shizuku.client.GalaxyFieldRenderer;
import com.buwan.shizuku.client.PhantomSlashInput;
import com.buwan.shizuku.client.ShizukuClientSetup;
import com.buwan.shizuku.client.shader.CosmicShaders;
import com.buwan.shizuku.item.MeowStoneItem;
import com.buwan.shizuku.network.PacketHandler;
import com.buwan.shizuku.registry.ModBlocks;
import com.buwan.shizuku.registry.ModMenus;
import com.buwan.shizuku.registry.ModRecipeSerializers;
import com.buwan.shizuku.registry.ModRecipeTypes;
import com.buwan.shizuku.registry.ModTileEntities;
import com.buwan.shizuku.registry.SAComboRegistry;
import com.buwan.shizuku.registry.SASlashArtRegistry;
import com.buwan.shizuku.registry.other.LBEntiteRegristrys;
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
import com.buwan.shizuku.se.SERegistry;
import mods.flammpfeil.slashblade.SlashBladeCreativeGroup;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.stream.Stream;

@Mod(shizuku.MODID)
public class shizuku {

    public static ResourceLocation prefix(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static final String MODID = "shizuku";
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    // 只保留物品注册器（用于喵石）
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("shizuku_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.shizuku_tab"))
                    .withTabsBefore(SlashBladeCreativeGroup.SLASHBLADE_GROUP.getKey())
                    .icon(() -> {
                        ItemStack stack = new ItemStack(SlashBladeItems.SLASHBLADE.get());
                        BladeStateAccess.of(stack).ifPresent(s -> {
                            s.setModel(prefix("model/shizuku/shizuku.obj"));
                            s.setTexture(prefix("model/shizuku/shizuku.png"));
                        });
                        return stack;
                    })
                    .displayItems((parameters, output) -> fillBlades(parameters.holders(), output))
                    .build()
    );

    // 喵石物品注册
    public static final DeferredHolder<Item, Item> MEOW_STONE = ITEMS.register("meow_stone",
            () -> new MeowStoneItem(new Item.Properties()));

    // 雫工作台物品注册（3.5.0）
    public static final DeferredHolder<Item, Item> SHIZUKU_CRAFTING_TABLE_ITEM = ITEMS.register(
            "shizuku_crafting_table",
            () -> new BlockItem(ModBlocks.SHIZUKU_CRAFTING_TABLE.get(), new Item.Properties()));

    private static void fillBlades(HolderLookup.Provider registries, CreativeModeTab.Output output) {
        HolderLookup.RegistryLookup<SlashBladeDefinition> lookup =
                registries.lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY);

        lookup.listElements()
                .filter(ref -> ref.key() != null && ref.value() != null)
                .filter(ref -> ref.key().location().getNamespace().equals(MODID))
                .sorted(Comparator.comparing(ref -> ref.key().location()))
                .flatMap(ref -> {
                    LOGGER.info("Registering Slashblade: {}", ref.key().location());
                    // canonical：按当前注册表重建附魔 Holder，否则拖出创造页时编码失败会掉线
                    ItemStack blade = com.buwan.shizuku.util.BladeStacks.canonical(
                            ref.value().getBlade(registries), registries);
                    return blade.isEmpty() ? Stream.empty() : Stream.of(blade);
                })
                .forEach(output::accept);

        // 3.5.0：无名无铭测试刀（连段根 Leisa + 喵喵 SE），不设名称/模型——薙草之稻光是 el 命名刀
        ItemStack engulfingLightning = new ItemStack(SlashBladeItems.SLASHBLADE.get());
        BladeStateAccess.of(engulfingLightning).ifPresent(s -> {
            s.setComboRoot(SASlashArtRegistry.Leisa.getId());
            s.addSpecialEffect(SERegistry.SHIZUKU.getId());
        });
        output.accept(engulfingLightning);

        // 喵石加入创造标签页
        output.accept(MEOW_STONE.get());
        // 雫工作台加入创造标签页（3.5.0）
        output.accept(SHIZUKU_CRAFTING_TABLE_ITEM.get());
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> LEISA_SOUND1 = registerSound("leisa_sound1");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEISA_SOUND2 = registerSound("leisa_sound2");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEISA_SOUND3 = registerSound("leisa_sound3");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLASS_BREAK_SOUND = registerSound("glass_break_sound");

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(prefix(name)));
    }

    public shizuku(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModTileEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModRecipeTypes.RECIPES.register(modEventBus);
        ModRecipeSerializers.SERIALIZERS.register(modEventBus);
        SERegistry.register(modEventBus);
        LBEntiteRegristrys.register(modEventBus);
        SAComboRegistry.COMBO_STATES.register(modEventBus);
        SASlashArtRegistry.SLASH_ARTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        ITEMS.register(modEventBus);

        modEventBus.addListener(PacketHandler::register);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(TeleportSlash.class);
        NeoForge.EVENT_BUS.register(TimeSlash.class);
        NeoForge.EVENT_BUS.register(TornadoSlash.class);
        NeoForge.EVENT_BUS.register(UnlimitedBladeWorks.class);
        NeoForge.EVENT_BUS.register(VoidPrison.class);
        NeoForge.EVENT_BUS.register(MoonlightSlash.class);
        NeoForge.EVENT_BUS.register(TsubameGaeshi.class);
        NeoForge.EVENT_BUS.register(Yingsa.class);
        // Ymsa 3.5.0 重写后自身带 @SubscribeEvent 方法，直接注册类
        NeoForge.EVENT_BUS.register(Ymsa.class);
        NeoForge.EVENT_BUS.register(SummerSlash.class);
        // 3.6.0：星河领域（服务端领域伤害逻辑）
        NeoForge.EVENT_BUS.register(GalaxyField.class);
        // BladeDurabilityFix 已由 @EventBusSubscriber 自动注册，无需重复 register

        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(ModMenus::onClientSetup);
            // 3.6.0：宇宙着色器注册 + 图集拼接后抓取 cosmic_0..9 sprite UV
            modEventBus.addListener(CosmicShaders.ModEvents::onRegisterShaders);
            modEventBus.addListener(CosmicShaders.ModEvents::onTextureStitch);
            ShizukuClientSetup.register();
        }
    }

    // 怪物死亡事件处理（掉落喵石）
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // 检查是否为原版敌对生物
        if (isHostileMob(entity)) {
            // 1% 掉落概率
            if (entity.getRandom().nextFloat() < 0.01f) {
                // 在死亡位置掉落喵石
                entity.spawnAtLocation(MEOW_STONE.get());
            }
        }
    }

    // 判断是否为原版敌对生物
    private boolean isHostileMob(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        return type == EntityType.ZOMBIE ||
                type == EntityType.SKELETON ||
                type == EntityType.CREEPER ||
                type == EntityType.SPIDER ||
                type == EntityType.CAVE_SPIDER ||
                type == EntityType.ENDERMAN ||
                type == EntityType.WITCH ||
                type == EntityType.BLAZE ||
                type == EntityType.GHAST ||
                type == EntityType.MAGMA_CUBE ||
                type == EntityType.SLIME;
    }
}
