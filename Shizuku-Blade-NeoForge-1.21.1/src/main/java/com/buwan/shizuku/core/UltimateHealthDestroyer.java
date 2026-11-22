package com.buwan.shizuku.core;

import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.DataItem;
import net.minecraft.world.entity.Entity;

/**
 * 血量毁灭者：直接破坏实体的 SynchedEntityData（必要时连实体 id 一起改），
 * 让客户端认为该实体血量极低/已死。
 *
 * 只在其它手段全部失败时使用，破坏性最强。
 */
public class UltimateHealthDestroyer {
    private static final String ITEMS_BY_ID = "f_135345_";
    private static final String VALUE = "f_135391_";

    public static int executeEntityDataDestruction(Entity target, float newValue) {
        int modifications = 0;

        try {
            SynchedEntityData entityData = target.getEntityData();
            Field itemsByIdField = SynchedEntityData.class.getDeclaredField("f_135345_");
            itemsByIdField.setAccessible(true);
            Map<Integer, DataItem<?>> itemsById = (Map<Integer, DataItem<?>>)itemsByIdField.get(entityData);

            for (DataItem item : itemsById.values()) {
                try {
                    Object value = item.getValue();
                    if (value instanceof Number number) {
                        float floatValue = number.floatValue();
                        Field valueField = DataItem.class.getDeclaredField("f_135391_");
                        valueField.setAccessible(true);
                        if (value instanceof Float) {
                            valueField.set(item, newValue);
                        } else if (value instanceof Double) {
                            valueField.set(item, (double)newValue);
                        } else if (value instanceof Integer) {
                            valueField.set(item, (int)newValue);
                        }

                        modifications++;
                    }
                } catch (Throwable var12) {
                }
            }
        } catch (Throwable var13) {
        }

        return modifications;
    }

    private static boolean isReasonableHealthValue(float value, float currentHealth, float maxHealth) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return false;
        } else if (value < 0.0F) {
            return false;
        } else if (value > 1000000.0F) {
            return false;
        } else if (!(Math.abs(value - currentHealth) < 0.01F) && !(Math.abs(value - maxHealth) < 0.01F)) {
            float maxReference = Math.max(currentHealth, maxHealth);
            if (maxReference <= 0.0F) {
                maxReference = 20.0F;
            }

            return value <= maxReference * 5.0F && value >= maxReference * 0.01F;
        } else {
            return true;
        }
    }

    public static boolean forceSetEntityId(Entity target, int newId) {
        try {
            Field idField = getField(Entity.class, "id", "f_19848_");
            idField.setAccessible(true);
            idField.set(target, newId);
        } catch (Exception var31) {
            System.err.println("\u8bbe\u7f6eID\u5931\u8d25: " + var31.getMessage());
        }

        return true;
    }

    private static Field getField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Exception var71) {
            }
        }

        return null;
    }
}
