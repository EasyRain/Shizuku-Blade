package com.buwan.shizuku.registry;

import com.buwan.shizuku.network.PacketHandler;
import com.buwan.shizuku.se.SERegistry;
import com.buwan.shizuku.shizuku;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * 「猫羽雫」创造模式标签页（对应原版 Forge 的 {@code itemGroup.shizuku_tab}）。
 * 原版用 {@code withTabsBefore(SlashBladeCreativeGroup)} 排序，Fabric 无此 API，
 * 这里按注册顺序追加。
 */
public final class ModCreativeTabs {

    public static final ResourceKey<CreativeModeTab> SHIZUKU_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, shizuku.prefix("shizuku_tab"));

    private ModCreativeTabs() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SHIZUKU_TAB_KEY,
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup.shizuku_tab"))
                        .icon(ModCreativeTabs::createIcon)
                        .build());

        ItemGroupEvents.modifyEntriesEvent(SHIZUKU_TAB_KEY).register(ModCreativeTabs::fill);
    }

    /** 图标：无铭拔刀剑套上雫的模型/贴图。 */
    private static ItemStack createIcon() {
        ItemStack stack = new ItemStack(SBItems.SLASHBLADE);
        CapabilitySlashBlade.BLADESTATE.maybeGet(stack).ifPresent(state -> {
            state.setModel(new net.minecraft.resources.ResourceLocation("shizuku", "model/shizuku/shizuku.obj"));
            state.setTexture(new net.minecraft.resources.ResourceLocation("shizuku", "model/shizuku/shizuku.png"));
        });
        return stack;
    }

    private static void fill(FabricItemGroupEntries entries) {
        // 1) 所有 shizuku:* 命名刀（服务端数据包注册表可用时）
        RegistryAccess access = PacketHandler.registryAccess();
        if (access != null) {
            access.registryOrThrow(SlashBladeDefinition.REGISTRY_KEY).entrySet().stream()
                    .filter(e -> e.getKey().location().getNamespace().equals("shizuku"))
                    .sorted(java.util.Comparator.comparing(e -> e.getKey().location().toString()))
                    .forEach(e -> entries.accept(e.getValue().getBlade()));
        }

        // 2) 「噬雷」示例刀（连招根 + SE）
        ItemStack leisa = new ItemStack(SBItems.SLASHBLADE);
        CapabilitySlashBlade.BLADESTATE.maybeGet(leisa).ifPresent(state -> {
            state.setComboRoot(SASlashArtRegistry.Leisa.getId());
            state.addSpecialEffect(SERegistry.SHIZUKU.getId());
        });
        entries.accept(leisa);

        // 3) 本 mod 的方块/物品
        entries.accept(shizuku.MEOW_STONE.get());
        entries.accept(shizuku.SHIZUKU_CRAFTING_TABLE_ITEM.get());
    }
}
