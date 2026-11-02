package com.buwan.shizuku.compat;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * NeoForge {@code RecipeMatcher} 的最小替代：回溯匹配无序配方。
 * 匹配成功返回每个 ingredient 对应的输入栈索引数组，失败返回 {@code null}。
 */
public final class RecipeMatcher {
    private RecipeMatcher() {
    }

    public static int[] findMatches(List<ItemStack> stacks, NonNullList<Ingredient> ingredients) {
        if (stacks.size() != ingredients.size()) {
            return null;
        }
        boolean[] used = new boolean[stacks.size()];
        int[] result = new int[ingredients.size()];
        return backtrack(0, stacks, ingredients, used, result) ? result : null;
    }

    private static boolean backtrack(int idx, List<ItemStack> stacks, NonNullList<Ingredient> ingredients,
                                     boolean[] used, int[] result) {
        if (idx == ingredients.size()) {
            return true;
        }
        Ingredient ingredient = ingredients.get(idx);
        for (int j = 0; j < stacks.size(); j++) {
            if (!used[j] && ingredient.test(stacks.get(j))) {
                used[j] = true;
                result[idx] = j;
                if (backtrack(idx + 1, stacks, ingredients, used, result)) {
                    return true;
                }
                used[j] = false;
            }
        }
        return false;
    }
}
