package com.buwan.shizuku;


import com.buwan.shizuku.registry.ModItems;
import com.buwan.shizuku.registry.other.LBEntiteRegristrys;
import com.buwan.shizuku.registry.SAComboRegistry;
import com.buwan.shizuku.registry.SASlashArtRegistry;
import com.buwan.shizuku.se.SERegistry;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.recipes.*;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import java.util.function.Consumer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;

import java.util.function.Consumer;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.crafting.Recipe;
import java.util.function.Consumer;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.item.*;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.mojang.logging.LogUtils;
import mods.flammpfeil.slashblade.SlashBladeCreativeGroup;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.Map;

@Mod(shizuku.MODID)
public class shizuku {

    public static ResourceLocation prefix(String path) {
        return new ResourceLocation(MODID, path);
    }

    public static final String MODID = "shizuku";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    // 只保留物品注册器（用于喵石）
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("shizuku_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.shizuku_tab"))
                    .withTabsBefore(SlashBladeCreativeGroup.SLASHBLADE_GROUP.getId())
                    .icon(() -> {
                        ItemStack stack = new ItemStack(SBItems.slashblade);
                        stack.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(s -> {
                            s.setModel(new ResourceLocation(MODID, "model/shizuku/shizuku.obj"));
                            s.setTexture(new ResourceLocation(MODID, "model/shizuku/shizuku.png"));
                        });
                        return stack;
                    })
                    .displayItems((parameters, output) -> fillBlades(output))
                    .build()
    );

    // 只保留喵石物品注册
    public static final RegistryObject<Item> MEOW_STONE = ITEMS.register("meow_stone",
            () -> new Item(new Item.Properties()));

    private static void fillBlades(CreativeModeTab.Output output) {
        if (Minecraft.getInstance().getConnection() != null) {
            BladeModelManager.getClientSlashBladeRegistry().entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                    .filter(entry -> {
                        ResourceLocation loc = ResourceLocation.tryParse(entry.getKey().location().toString());
                        return loc != null && loc.getNamespace().equals(MODID);
                    })
                    .sorted(Map.Entry.comparingByKey())
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

            ItemStack ymBlade = new ItemStack(SBItems.slashblade);
            ymBlade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(s -> {
                s.setComboRoot(SASlashArtRegistry.Ymsa.getId());
            });
            output.accept(ymBlade);
        }

        // 只添加喵石到创意标签页
        output.accept(MEOW_STONE.get());
    }

    public static final RegistryObject<SoundEvent> LEISA_SOUND1 = registerSound("leisa_sound1");
    public static final RegistryObject<SoundEvent> LEISA_SOUND2 = registerSound("leisa_sound2");
    public static final RegistryObject<SoundEvent> LEISA_SOUND3 = registerSound("leisa_sound3");

    private static RegistryObject<SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(prefix(name)));
    }

    public shizuku() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        CREATIVE_MODE_TABS.register(modEventBus);
        SERegistry.register(modEventBus);
        LBEntiteRegristrys.register(modEventBus);
        SAComboRegistry.COMBO_STATES.register(modEventBus);
        SASlashArtRegistry.SLASH_ARTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);

        // 只注册ITEMS（喵石）
        ITEMS.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);
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