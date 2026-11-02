package com.buwan.shizuku.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 干净公共 API 实现：替代 1.20.1 的 SynchedEntityData/NBT/反射改血方案。
 */
public class HealthModifyHelper {

    public static boolean entityDataModify(Entity target, float newValue) {
        if (target instanceof LivingEntity le) {
            le.setHealth(clamp(newValue, le.getMaxHealth()));
            return true;
        }
        return false;
    }

    public static boolean traversalModify(Entity entity, Entity player, float newValue, boolean look) {
        if (entity instanceof LivingEntity le && !(le instanceof Player)) {
            le.setHealth(clamp(newValue, le.getMaxHealth()));
            return true;
        }
        return false;
    }

    public static boolean traversalModify(Entity entity, Entity player, float newValue) {
        return traversalModify(entity, player, newValue, true);
    }

    public static boolean forceModify(Entity entity, float newValue, float newMaxValue) {
        if (entity instanceof LivingEntity le) {
            le.setHealth(clamp(newValue, le.getMaxHealth()));
            AttributeInstance attr = le.getAttribute(Attributes.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(clamp(newMaxValue, Float.MAX_VALUE));
            }
            return true;
        }
        return false;
    }

    public static boolean tagModify(Entity entity, float newValue) {
        if (entity instanceof LivingEntity le) {
            le.setHealth(clamp(newValue, le.getMaxHealth()));
            return true;
        }
        return false;
    }

    public static boolean attributeModifier(Entity entity, float newMaxValue) {
        if (entity instanceof LivingEntity le) {
            AttributeInstance maxHealthAttribute = le.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttribute == null) {
                return false;
            }
            maxHealthAttribute.setBaseValue(clamp(newMaxValue, Float.MAX_VALUE));
            return true;
        }
        return false;
    }

    private static float clamp(float value, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0F;
        }
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
