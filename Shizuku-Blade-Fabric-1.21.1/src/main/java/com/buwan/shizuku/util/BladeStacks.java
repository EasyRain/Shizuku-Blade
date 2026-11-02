package com.buwan.shizuku.util;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 命名刀展示栈的规范化。
 *
 * <p>问题：{@code SlashBladeDefinition.getBlade()} 构造出来的刀，其 {@code ItemEnchantments} 里的
 * 附魔 {@code Holder} 绑在**数据包加载时的注册表实例**上。客户端之后网络编码这个栈时用的是
 * <b>当前</b>注册表，找不到对应 id 就会抛
 * {@code IllegalArgumentException: Can't find id for 'Reference{...}'}，
 * 表现为从 JEI / 创造页把刀拖出来时直接掉线（set_creative_mode_slot 编码失败）。</p>
 *
 * <p>做法：按附魔的 {@code ResourceKey} 到**当前注册表**重新取 Holder，重建
 * {@code ENCHANTMENTS} 组件；取不到的（没有 key 的直接 Holder）跳过，避免再次产生不可编码的栈。</p>
 */
public final class BladeStacks {

    private BladeStacks() {
    }

    public static ItemStack canonical(ItemStack stack, HolderLookup.Provider access) {
        if (stack.isEmpty() || access == null) {
            return stack;
        }
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) {
            return stack;
        }
        HolderLookup.RegistryLookup<Enchantment> lookup = access.lookupOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments.Mutable rebuilt = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            ResourceLocation id = entry.getKey().unwrapKey()
                    .map(ResourceKey::location)
                    .orElse(null);
            if (id == null) {
                continue;
            }
            lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id))
                    .ifPresent(holder -> rebuilt.set(holder, entry.getIntValue()));
        }
        ItemStack copy = stack.copy();
        copy.set(DataComponents.ENCHANTMENTS, rebuilt.toImmutable());
        return copy;
    }
}
