package com.buwan.shizuku.core;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 干净公共 API 实现：替代 1.20.1 的反射读私有无字段方案。
 */
public class EntityHelper {

    /**
     * 强制设置生命值：先把 health clamp 到 [0, 当前生命上限]（NaN/±Infinity 视作 0），再 setHealth。
     */
    public static void forcedSetHealth(LivingEntity entity, float health) {
        float safe = sanitize(health);
        float max = entity.getMaxHealth();
        if (safe > max) {
            safe = max;
        }
        entity.setHealth(safe);
    }

    /**
     * 强制设置生命上限：clamp（NaN/±Infinity 与负数视作 0），写入 MAX_HEALTH 属性 base value。
     */
    public static void forcedSetMaxHealth(LivingEntity entity, float maxHealth) {
        float safe = sanitize(maxHealth);
        AttributeInstance attr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(safe);
        }
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
