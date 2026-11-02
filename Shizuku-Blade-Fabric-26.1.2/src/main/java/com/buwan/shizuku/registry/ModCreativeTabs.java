package com.buwan.shizuku.registry;

import com.buwan.shizuku.network.PacketHandler;
import com.buwan.shizuku.se.SERegistry;
import com.buwan.shizuku.shizuku;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;

/**
 * 「猫羽雫」创造模式标签页（对应原版 Forge 的 {@code itemGroup.shizuku_tab}）。
 * 26.1：Fabric 的 item-group API 改名为 fabric-creative-tab-api-v1。
 */
public final class ModCreativeTabs {

    public static final ResourceKey<CreativeModeTab> SHIZUKU_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, shizuku.prefix("shizuku_tab"));

    private ModCreativeTabs() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SHIZUKU_TAB_KEY,
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.shizuku_tab"))
                        .icon(ModCreativeTabs::createIcon)
                        .build());

        CreativeModeTabEvents.modifyOutputEvent(SHIZUKU_TAB_KEY).register(ModCreativeTabs::fill);
    }

    private static ItemStack createIcon() {
        ItemStack stack = new ItemStack(SBItems.SLASHBLADE);
        CapabilitySlashBlade.getBladeState(stack).ifPresent(state -> {
            state.setModel(Identifier.fromNamespaceAndPath("shizuku", "model/shizuku/shizuku.obj"));
            state.setTexture(Identifier.fromNamespaceAndPath("shizuku", "model/shizuku/shizuku.png"));
        });
        return stack;
    }

    private static void fill(FabricCreativeModeTabOutput output) {
        RegistryAccess access = PacketHandler.registryAccess();
        if (access != null) {
            access.lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements()
                    .filter(ref -> ref.key().identifier().getNamespace().equals("shizuku"))
                    .sorted(Comparator.comparing(ref -> ref.key().identifier().toString()))
                    .forEach(ref -> output.accept(ref.value().getBlade()));
        }

        ItemStack leisa = new ItemStack(SBItems.SLASHBLADE);
        CapabilitySlashBlade.getBladeState(leisa).ifPresent(state -> {
            state.setComboRoot(SASlashArtRegistry.Leisa.getId());
            state.addSpecialEffect(SERegistry.SHIZUKU.getId());
        });
        output.accept(leisa);

        output.accept(shizuku.MEOW_STONE.get());
        output.accept(shizuku.SHIZUKU_CRAFTING_TABLE_ITEM.get());
    }
}
