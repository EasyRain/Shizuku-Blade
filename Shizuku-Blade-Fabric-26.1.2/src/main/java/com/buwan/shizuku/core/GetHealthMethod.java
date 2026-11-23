package com.buwan.shizuku.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 最底层的反射血量读取：遍历字段找 float/数值型血量，
 * 并可从 Attributes.MAX_HEALTH 取最大血量。
 *
 * 在 Agent 不可用时作为兜底路径。
 */
class GetHealthMethod {
    public static float getHealthFromFields(Object entity) throws Exception {
        for (Class<?> clazz = entity.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(entity);
                if (value instanceof Float) {
                    float floatValue = (Float)value;
                    if (floatValue > 0.0F && floatValue <= 10000.0F) {
                        return floatValue;
                    }
                }
            }
        }

        return 20.0F;
    }

    public static float getMaxHealthFromAttributes(Object entity) throws Exception {
        try {
            Method getAttributeMethod = entity.getClass().getMethod("getAttribute", Class.forName("net.minecraft.world.entity.ai.attributes.Attributes"));
            Object maxHealthAttribute = Class.forName("net.minecraft.world.entity.ai.attributes.Attributes").getField("MAX_HEALTH").get(null);
            Object attributeInstance = getAttributeMethod.invoke(entity, maxHealthAttribute);
            Method getValueMethod = attributeInstance.getClass().getMethod("getValue");
            return (Float)getValueMethod.invoke(attributeInstance);
        } catch (Exception var5) {
            return 20.0F;
        }
    }
}
