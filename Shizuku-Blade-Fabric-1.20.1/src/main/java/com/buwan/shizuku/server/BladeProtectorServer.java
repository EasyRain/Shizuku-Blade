package com.buwan.shizuku.server;

import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;






import java.util.Set;


public class BladeProtectorServer {

    private static final Set<ResourceLocation> PROTECTED_BLADES = Set.of(
            new ResourceLocation("shizuku", "shizuku"),
            new ResourceLocation("shizuku", "el"),
            new ResourceLocation("shizuku", "ym")
    );

    public static boolean isProtectedBlade(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            if (stack.getItem() instanceof ItemSlashBlade) {
                String displayName = stack.getDisplayName().getString();
                if (displayName.contains("无铭") && displayName.contains("有名")) {
                    return true;
                }

                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (tag.contains("display")) {
                    CompoundTag display = tag.getCompound("display");
                    if (display.contains("Name")) {
                        String nbtName = display.getString("Name");
                        if (nbtName.contains("无铭") && nbtName.contains("有名")) {
                            return true;
                        }
                    }
                }

                if (tag.contains("SB.SwordName")) {
                    String bladeName = tag.getString("SB.SwordName");
                    if (bladeName.contains("无铭") && bladeName.contains("有名")) {
                        return true;
                    }
                }

                if (!tag.contains("shizuku:protected_blade") && !tag.contains("shizuku:keep_on_death")) {
                    return CapabilitySlashBlade.BLADESTATE.maybeGet(stack).map(state -> {
                        String bladeNamex = tag.getString("SB.SwordName");
                        return bladeNamex != null && (bladeNamex.contains("shizuku")
                                || bladeNamex.contains("el") || bladeNamex.contains("ym"));
                    }).orElse(false);
                }

                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    
    public static void onPlayerLoggedIn(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        System.out.println("§6[BladeProtectorServer] 玩家登录，确保保护标签...");
        ensureProtectionTags(player);
        System.out.println("§6[BladeProtectorServer] 保护标签确保完成！");
    }

    
    public static void onPlayerDeath(LivingEntity entity) {
        if (!(entity instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        System.out.println("§6[BladeProtectorServer] 服务器端死亡保护触发");

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isProtectedBlade(stack)) {
                applyProtectionTags(stack);
                player.getInventory().setItem(i, stack);
                System.out.println("§6[BladeProtectorServer] 服务器端保护物品: " + stack.getDisplayName().getString());
            }
        }

        ItemStack mainHand = player.getMainHandItem();
        if (isProtectedBlade(mainHand)) {
            applyProtectionTags(mainHand);
            player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
            System.out.println("§6[BladeProtectorServer] 服务器端保护主手物品: " + mainHand.getDisplayName().getString());
        }

        ItemStack offHand = player.getOffhandItem();
        if (isProtectedBlade(offHand)) {
            applyProtectionTags(offHand);
            player.setItemInHand(InteractionHand.OFF_HAND, offHand);
            System.out.println("§6[BladeProtectorServer] 服务器端保护副手物品: " + offHand.getDisplayName().getString());
        }
    }

    
    private static void ensureProtectionTags(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemSlashBlade && isProtectedBlade(stack)) {
                applyProtectionTags(stack);
                player.getInventory().setItem(i, stack);
            }
        }
    }

    private static void applyProtectionTags(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean("shizuku:keep_on_death", true);
        tag.putString("shizuku:protected_blade", "true");
        tag.putLong("shizuku:protection_timestamp", System.currentTimeMillis());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
