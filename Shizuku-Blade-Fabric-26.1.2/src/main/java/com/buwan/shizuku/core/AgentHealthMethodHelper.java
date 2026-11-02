package com.buwan.shizuku.core;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 干净公共 API 实现：替代 1.20.1 的 java agent / ASM 反射改血方案。
 * 仅使用 1.21.1 原版公开方法。
 */
public class AgentHealthMethodHelper {

    /**
     * 强制设置实体的生命值与生命上限。
     * <p>
     * 语义：先把 health clamp 到 [0, 上限]（NaN/±Infinity 视作 0），setHealth；
     * 再设置 MAX_HEALTH 属性 base value（同样 clamp，NaN/±Infinity 与负数视作 0）。
     */
    public static void forceSetHealth(Entity entity, float health, float maxHealth) {
        if (entity instanceof LivingEntity living) {
            float safeMax = sanitize(maxHealth);
            float safeHealth = sanitize(health);
            if (safeHealth > safeMax) {
                safeHealth = safeMax;
            }
            living.setHealth(safeHealth);

            AttributeInstance maxHealthAttr = living.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(safeMax);
            }
        }
    }

    /** 空实现：1.21.1 不再维护 agent 端的临时血量缓存。 */
    public static void clear(Entity entity) {
        // no-op
    }

    private static float sanitize(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0F;
        }
        if (value < 0.0F) {
            return 0.0F;
        }
        return value;
    }
}
