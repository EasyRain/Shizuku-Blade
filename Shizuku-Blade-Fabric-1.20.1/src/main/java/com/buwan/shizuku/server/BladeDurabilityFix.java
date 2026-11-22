package com.buwan.shizuku.server;

import com.buwan.shizuku.sa.UnleashedJudgment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 修复无铭「有名」攻击时的负数附魔等级。
 *
 * <p>Fabric 版不再订阅 Forge 的 LivingAttackEvent：由 {@code AttackEntityCallback}
 * （在 {@code shizuku#init()} 里注册）调用 {@link #onLivingAttack(net.minecraft.world.entity.LivingEntity)}。</p>
 */
public class BladeDurabilityFix {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("shizuku/bladedurabilityfix");
    /** 攻击者挥击时修一次附魔等级（由 Fabric 的攻击回调传入攻击者）。 */
    public static void onLivingAttack(LivingEntity attacker) {
        if (attacker != null) {
            fixBladeEnchantmentLevels(attacker.getMainHandItem());
        }
    }

    public static void fixBladeEnchantmentLevels(ItemStack stack) {
        if (UnleashedJudgment.isBlade(stack)) {
            try {
                if (stack.hasTag() && stack.getOrCreateTag().contains("Enchantments")) {
                    ListTag enchantments = stack.getEnchantmentTags();
                    boolean hasNegativeLevel = false;

                    for (int i = 0; i < enchantments.size(); i++) {
                        CompoundTag enchantmentTag = enchantments.getCompound(i);
                        if (enchantmentTag.contains("lvl")) {
                            int level = enchantmentTag.getInt("lvl");
                            if (level < 0) {
                                hasNegativeLevel = true;
                                enchantmentTag.putInt("lvl", 0);
                                LOGGER.debug(
                                        "[BladeDurabilityFix] \u4fee\u590d\u8d1f\u6570\u9644\u9b54\u7b49\u7ea7: "
                                            + enchantmentTag.getString("id")
                                            + " \u4ece "
                                            + level
                                            + " \u5230 0"
                                    );
                            }
                        }
                    }

                    if (hasNegativeLevel) {
                        LOGGER.debug("[BladeDurabilityFix] \u5df2\u4fee\u590d\u62d4\u5200\u5251\u9644\u9b54: " + stack.getHoverName().getString());
                    }
                }
            } catch (Exception var6) {
                LOGGER.debug("unexpected error", var6);
            }
        }
    }
}
