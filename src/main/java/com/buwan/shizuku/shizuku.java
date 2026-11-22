package com.buwan.shizuku;

import com.buwan.shizuku.client.CosmicBladeRenderer;
import com.buwan.shizuku.client.FieldActivateInput;
import com.buwan.shizuku.client.JudgmentVisuals;
import com.buwan.shizuku.client.GalaxyFieldRenderer;
import com.buwan.shizuku.client.SynthwaveShaderRegister;
import com.buwan.shizuku.client.SynthwaveShaderRenderer;
import com.buwan.shizuku.client.TimeStopRenderer;
import com.buwan.shizuku.client.TimeStopScreenEffect;
import com.buwan.shizuku.client.VoidPrisonRenderer;
import com.buwan.shizuku.client.shader.CosmicShaders;
import com.buwan.shizuku.client.tooltip.ShizukuTooltipRenderer;
import com.buwan.shizuku.client.tooltip.TooltipEventHandler;
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
import com.mojang.logging.LogUtils;
import java.util.Map.Entry;
import mods.flammpfeil.slashblade.SlashBladeCreativeGroup;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.item.CreativeModeTab.Output;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * 主类：注册全部 DeferredRegister（物品/方块/音效/配方/实体/菜单/tile）、创造模式标签页、
 * 并挂上事件总线与客户端初始化。
 *
 * 创造栏的刀列表是运行时从 SlashBlade 的客户端定义注册表里筛 shizuku 命名空间得到的，
 * 所以新增命名刀只需加数据文件，不用改这里。
 */
@Mod("shizuku")
public class shizuku {
    public static final String MODID = "shizuku";
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "shizuku");
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "shizuku");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "shizuku");
    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register(
        "shizuku_tab",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.shizuku_tab"))
            .withTabsBefore(new ResourceLocation[]{SlashBladeCreativeGroup.SLASHBLADE_GROUP.getId()})
            .icon(() -> {
                ItemStack stack = new ItemStack(SBItems.slashblade);
                stack.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(s -> {
                    s.setModel(new ResourceLocation("shizuku", "model/shizuku/shizuku.obj"));
                    s.setTexture(new ResourceLocation("shizuku", "model/shizuku/shizuku.png"));
                });
                return stack;
            })
            .displayItems((parameters, output) -> fillBlades(output))
            .build()
    );
    public static final RegistryObject<Item> MEOW_STONE = ITEMS.register("meow_stone", () -> new MeowStoneItem(new Properties()));
    public static final RegistryObject<Item> SHIZUKU_CRAFTING_TABLE_ITEM = ITEMS.register(
        "shizuku_crafting_table", () -> new BlockItem((Block)ModBlocks.SHIZUKU_CRAFTING_TABLE.get(), new Properties())
    );
    public static final RegistryObject<SoundEvent> LEISA_SOUND1 = registerSound("leisa_sound1");
    public static final RegistryObject<SoundEvent> LEISA_SOUND2 = registerSound("leisa_sound2");
    public static final RegistryObject<SoundEvent> LEISA_SOUND3 = registerSound("leisa_sound3");
    public static final RegistryObject<SoundEvent> GLASS_BREAK_SOUND = registerSound("glass_break_sound");

    public static ResourceLocation prefix(String path) {
        return new ResourceLocation("shizuku", path);
    }

    private static void fillBlades(Output output) {
        if (Minecraft.getInstance().getConnection() != null) {
            BladeModelManager.getClientSlashBladeRegistry()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .filter(entry -> {
                    ResourceLocation loc = ResourceLocation.tryParse(entry.getKey().location().toString());
                    return loc != null && loc.getNamespace().equals("shizuku");
                })
                .sorted(Entry.comparingByKey())
                .forEach(entry -> {
                    LOGGER.info("Registering Slashblade: {}", entry.getKey());
                    output.accept(entry.getValue().getBlade());
                });
            ItemStack engulfingLightning = new ItemStack(SBItems.slashblade);
            engulfingLightning.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(s -> {
                s.setComboRoot(SASlashArtRegistry.Leisa.getId());
                s.addSpecialEffect(SERegistry.SHIZUKU.getId());
            });
            output.accept(engulfingLightning);
        }

        output.accept((ItemLike)MEOW_STONE.get());
        output.accept((ItemLike)SHIZUKU_CRAFTING_TABLE_ITEM.get());
    }

    private static RegistryObject<SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(prefix(name)));
    }

    public shizuku() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, com.buwan.shizuku.sa.JudgmentConfig.SPEC);
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
        if (FMLEnvironment.dist.isClient()) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, JudgmentVisuals.CONFIG);
            // Mods 列表里的「配置」按钮：注册 Forge 的配置界面扩展点，直接打开我们自己的配置页
            // （以前没注册 ⇒ 按钮是灰的；Forge 1.20.1 只认这个扩展点，不认 GUI 工厂之类的约定）
            ModLoadingContext.get().registerExtensionPoint(
                net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                    (mc, parent) -> new com.buwan.shizuku.client.screen.JudgmentScreen(parent)));
            modEventBus.addListener(SynthwaveShaderRegister::registerShaders);
            modEventBus.addListener(CosmicShaders.ModEvents::onRegisterShaders);
            modEventBus.addListener(CosmicShaders.ModEvents::onTextureStitch);
            // 必须写明事件类型：反编译丢泛型后 lambda 会推断成 Event，模组总线要求 IModBusEvent，运行期会直接抛
            modEventBus.addListener((FMLClientSetupEvent event) -> {
                ModMenus.onClientSetup();
                // 解放能力默认关闭：每次启动把本地开关强制复位，避免上一次会话的状态残留。
                // 能否真正开启由服务端 OP 校验决定，这里只保证界面从"关闭"态起步。
                com.buwan.shizuku.client.JudgmentVisuals.UNLEASHED.set(false);
                com.buwan.shizuku.client.JudgmentVisuals.UNLEASHED.save();
            });
        }

        ITEMS.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(TeleportSlash.class);
        MinecraftForge.EVENT_BUS.register(TimeSlash.class);
        MinecraftForge.EVENT_BUS.register(TornadoSlash.class);
        MinecraftForge.EVENT_BUS.register(UnlimitedBladeWorks.class);
        MinecraftForge.EVENT_BUS.register(VoidPrison.class);
        MinecraftForge.EVENT_BUS.register(MoonlightSlash.class);
        MinecraftForge.EVENT_BUS.register(TsubameGaeshi.class);
        MinecraftForge.EVENT_BUS.register(Yingsa.class);
        MinecraftForge.EVENT_BUS.register(Ymsa.class);
        MinecraftForge.EVENT_BUS.register(SummerSlash.class);
        MinecraftForge.EVENT_BUS.register(GalaxyField.class);
        modEventBus.addListener(this::commonSetup);
        if (FMLEnvironment.dist.isClient()) {
            MinecraftForge.EVENT_BUS.register(TimeStopRenderer.getInstance());
            MinecraftForge.EVENT_BUS.register(TimeStopScreenEffect.getInstance());
            MinecraftForge.EVENT_BUS.register(VoidPrisonRenderer.getInstance());
            MinecraftForge.EVENT_BUS.register(CosmicShaders.ForgeEvents.class);
            MinecraftForge.EVENT_BUS.register(CosmicBladeRenderer.class);
            MinecraftForge.EVENT_BUS.register(GalaxyFieldRenderer.class);
            MinecraftForge.EVENT_BUS.register(FieldActivateInput.class);
            MinecraftForge.EVENT_BUS.register(TooltipEventHandler.class);
            MinecraftForge.EVENT_BUS.register(ShizukuTooltipRenderer.class);
            MinecraftForge.EVENT_BUS.register(SynthwaveShaderRenderer.class);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PacketHandler.register();
            // 启动自检：把 Unsafe 解析到的字段偏移打出来。
            // 负数表示该字段在当前命名环境（dev=Mojang 名 / 生产=SRG 名）里没找到，
            // 对应能力会自动跳过而不是抛异常。
            LOGGER.info("[Shizuku] \u5ba1\u5224\u5904\u51b3\u5c42\u81ea\u68c0: {}", com.buwan.shizuku.sa.JudgmentExecution.describe());
        });
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        // 概率可在游戏内配置页 / config/shizuku-server.toml 里调（0 表示关闭）
        if (this.isHostileMob(entity) && entity.getRandom().nextFloat() < com.buwan.shizuku.sa.JudgmentConfig.meowStoneDropChance()) {
            entity.spawnAtLocation((ItemLike)MEOW_STONE.get());
        }
    }

    private boolean isHostileMob(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        return type == EntityType.ZOMBIE
            || type == EntityType.SKELETON
            || type == EntityType.CREEPER
            || type == EntityType.SPIDER
            || type == EntityType.CAVE_SPIDER
            || type == EntityType.ENDERMAN
            || type == EntityType.WITCH
            || type == EntityType.BLAZE
            || type == EntityType.GHAST
            || type == EntityType.MAGMA_CUBE
            || type == EntityType.SLIME;
    }
}
