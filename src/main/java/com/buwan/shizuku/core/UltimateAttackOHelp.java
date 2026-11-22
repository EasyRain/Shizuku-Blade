package com.buwan.shizuku.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData.DataItem;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「智能改血」：在目标身上找最像血量的数值字段（排除 static 与 EntityDataAccessor 字段），
 * 按与参考血量的接近程度排序，命中即改。
 *
 * 用于血量藏在自定义字段、getHealth 又拿不到的 boss。
 */
public class UltimateAttackOHelp {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("shizuku/ultimateattackohelp");
    public static void smartModifyMode(LivingEntity target, float referenceHealth, float newValue, boolean titan, boolean lock) {
        try {
            float lowerBound = referenceHealth * 0.95F;
            float upperBound = Float.POSITIVE_INFINITY;
            List<UltimateAttackOHelp.FieldInfo> candidates = findCandidateFields(target, lowerBound, upperBound, referenceHealth, newValue);
            if (candidates.isEmpty()) {
                return;
            }

            candidates.sort((a, b) -> {
                boolean aExact = a.value == referenceHealth;
                boolean bExact = b.value == referenceHealth;
                if (aExact && !bExact) {
                    return -1;
                } else {
                    return !aExact && bExact ? 1 : Float.compare(Math.abs(a.value - referenceHealth), Math.abs(b.value - referenceHealth));
                }
            });

            for (UltimateAttackOHelp.FieldInfo info : candidates) {
                try {
                    info.modifier.run();
                } catch (Exception var11) {
                }
            }
        } catch (Exception var12) {
            LOGGER.debug("unexpected error", var12);
        }
    }

    private static List<UltimateAttackOHelp.FieldInfo> findCandidateFields(
        LivingEntity target, float lowerBound, float upperBound, float targetHealth, float newValue
    ) {
        List<UltimateAttackOHelp.FieldInfo> candidates = new ArrayList<>();
        ArrayList exactMatches = new ArrayList();

        try {
            try {
                Field[] fields = getAllFields(target.getClass());

                for (Field field : fields) {
                    if (EntityDataAccessor.class.isAssignableFrom(field.getType()) && Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);

                        try {
                            EntityDataAccessor<?> accessor = (EntityDataAccessor<?>)field.get(null);
                            if (accessor != null) {
                                Object value = target.getEntityData().get(accessor);
                                float numValue = extractNumericValue(value);
                                if (numValue >= lowerBound && numValue <= upperBound) {
                                    Runnable modifier = () -> {
                                        try {
                                            Field itemsByIdField = target.getEntityData().getClass().getDeclaredField("f_135345_");
                                            itemsByIdField.setAccessible(true);
                                            Map<Integer, Object> itemsById = (Map<Integer, Object>)itemsByIdField.get(target.getEntityData());
                                            DataItem<?> item = (DataItem<?>)itemsById.get(accessor.getId());
                                            if (item != null) {
                                                Field valueField = DataItem.class.getDeclaredField("f_135391_");
                                                valueField.setAccessible(true);
                                                valueField.set(item, convertToTargetType((int)newValue, value));
                                            }
                                        } catch (Exception var9) {
                                            try {
                                                Method setMethod = target.getEntityData().getClass().getMethod("set", EntityDataAccessor.class, Object.class);
                                                setMethod.invoke(target.getEntityData(), accessor, convertToTargetType((int)newValue, value));
                                            } catch (Exception var8) {
                                            }
                                        }
                                    };
                                    UltimateAttackOHelp.FieldInfo info = new UltimateAttackOHelp.FieldInfo(
                                        modifier, numValue, "EntityDataAccessor: " + field.getName()
                                    );
                                    if (numValue == targetHealth) {
                                        exactMatches.add(info);
                                    } else {
                                        candidates.add(info);
                                    }
                                }
                            }
                        } catch (Exception var18) {
                        }
                    }
                }
            } catch (Exception var20) {
            }

            try {
                Field[] fields = getAllFields(target.getClass());

                for (Field fieldx : fields) {
                    if (!Modifier.isStatic(fieldx.getModifiers()) && !EntityDataAccessor.class.isAssignableFrom(fieldx.getType())) {
                        fieldx.setAccessible(true);

                        try {
                            Object value = fieldx.get(target);
                            if (value != null && isNumericType(value.getClass())) {
                                float numValue = extractNumericValue(value);
                                if (numValue >= lowerBound && numValue <= upperBound) {
                                    Runnable modifier = () -> setFieldValue(fieldx, target, (int)newValue);
                                    UltimateAttackOHelp.FieldInfo info = new UltimateAttackOHelp.FieldInfo(modifier, numValue, "Field: " + fieldx.getName());
                                    if (numValue == targetHealth) {
                                        exactMatches.add(info);
                                    } else {
                                        candidates.add(info);
                                    }
                                }
                            }
                        } catch (Exception var17) {
                        }
                    }
                }
            } catch (Exception var19) {
            }
        } catch (Exception var21) {
            LOGGER.debug("unexpected error", var21);
        }

        return (List<UltimateAttackOHelp.FieldInfo>)(!exactMatches.isEmpty() ? exactMatches : candidates);
    }

    private static Field[] getAllFields(Class<?> clazz) {
        ArrayList<Field> fields = new ArrayList<>();

        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }

        return fields.toArray(new Field[0]);
    }

    private static boolean isNumericType(Class<?> type) {
        return type == int.class
            || type == Integer.class
            || type == float.class
            || type == Float.class
            || type == double.class
            || type == Double.class
            || type == long.class
            || type == Long.class
            || type == short.class
            || type == Short.class
            || type == byte.class
            || type == Byte.class;
    }

    private static float extractNumericValue(Object value) {
        return value instanceof Number ? ((Number)value).floatValue() : 0.0F;
    }

    private static Object convertToTargetType(int newValue, Object originalValue) {
        if (originalValue instanceof Integer) {
            return newValue;
        } else if (originalValue instanceof Float) {
            return (float)newValue;
        } else if (originalValue instanceof Double) {
            return (double)newValue;
        } else if (originalValue instanceof Long) {
            return (long)newValue;
        } else {
            return originalValue instanceof Short ? (short)newValue : originalValue instanceof Byte ? (byte)newValue : newValue;
        }
    }

    private static void setFieldValue(Field field, Object target, int newValue) {
        try {
            Class<?> type = field.getType();
            if (type == int.class || type == Integer.class) {
                field.set(target, newValue);
            } else if (type == float.class || type == Float.class) {
                field.set(target, (float)newValue);
            } else if (type == double.class || type == Double.class) {
                field.set(target, (double)newValue);
            } else if (type == long.class || type == Long.class) {
                field.set(target, (long)newValue);
            } else if (type == short.class || type == Short.class) {
                field.set(target, (short)newValue);
            } else if (type == byte.class || type == Byte.class) {
                field.set(target, (byte)newValue);
            }
        } catch (Exception var4) {
            LOGGER.debug("unexpected error", var4);
        }
    }

    private static class FieldInfo {
        final Runnable modifier;
        final float value;
        final String description;

        FieldInfo(Runnable modifier, float value, String description) {
            this.modifier = modifier;
            this.value = value;
            this.description = description;
        }
    }
}
