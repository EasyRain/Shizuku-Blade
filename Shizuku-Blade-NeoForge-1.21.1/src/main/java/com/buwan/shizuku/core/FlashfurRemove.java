package com.buwan.shizuku.core;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 干净公共 API 实现：替代 1.20.1 对第三方模组内部列表的 Unsafe 清空方案。
 */
public class FlashfurRemove {

    /** 空实现：不再通过 Unsafe 清空第三方模组的内部实体列表。 */
    public static void forceCleanList() {
        // no-op
    }

    /** 以最大伤害攻击目标。 */
    public static void attackWithMaxDamage(LivingEntity target, Player attacker) {
        target.hurt(attacker.damageSources().genericKill(), Float.MAX_VALUE);
    }
}
