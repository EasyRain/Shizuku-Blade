package com.buwan.shizuku.agent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.phys.Vec3;

/**
 * Agent 的反射工具箱：在字节码改写不可用时，用多重命名兜底地调方法 / 读写字段。
 *
 * 多数方法接受 String[] 候选名（如 {"setInvisible", "m_6842_"}），依次尝试，
 * 命中即返回——这也是本 mod 能跨多个前置版本工作的原因。
 */
public class AgentHelp {
    public static final String MOD_ID = "shizuku";

    public static boolean blackClass(String className) {
        if (className.startsWith("org/bukkit/") || className.startsWith("org/spigotmc/") || className.startsWith("com/destroystokyo/paper/")) {
            return true;
        } else if (className.startsWith("net/minecraft/server/") && className.contains("Craft")) {
            return true;
        } else if (className.startsWith("com/mohistmc/") || className.startsWith("red/mohist/")) {
            return true;
        } else if (className.startsWith("net/minecraftforge/") && !className.contains("event")) {
            return true;
        } else {
            return className.startsWith("com/buwan/shizuku/")
                ? true
                : className.startsWith("java/")
                    || className.startsWith("javax/")
                    || className.startsWith("sun/")
                    || className.startsWith("com/sun/")
                    || className.startsWith("jdk/");
        }
    }

    public static boolean remove(Object entity) {
        try {
            setFieldValue(entity, new String[]{"deathTime"}, 20);
            tryInvokeMethod(entity, new String[]{"setInvisible", "m_6842_"}, new Class[]{boolean.class}, new Object[]{true});
            tryInvokeMethod(entity, new String[]{"setInvulnerable", "m_20331_"}, new Class[]{boolean.class}, new Object[]{false});
            tryInvokeMethod(entity, new String[]{"setSilent"}, new Class[]{boolean.class}, new Object[]{true});
            tryInvokeMethod(entity, new String[]{"setNoGravity"}, new Class[]{boolean.class}, new Object[]{true});
            tryInvokeMethod(entity, new String[]{"setHealth", "m_21233_"}, new Class[]{float.class}, new Object[]{0.0F});
            tryInvokeMethod(entity, new String[]{"kill"}, new Class[0], new Object[0]);
            tryInvokeMethod(entity, new String[]{"setRemoved", "m_8093_"}, new Class[]{RemovalReason.class}, new Object[]{RemovalReason.KILLED});
            setFieldValue(entity, new String[]{"removalReason"}, RemovalReason.KILLED);
            setFieldValue(entity, new String[]{"removed"}, true);
            setFieldValue(entity, new String[]{"canUpdate"}, false);
            return true;
        } catch (Exception var2) {
            System.err.println("[Shizuku] remove failed: " + var2.getMessage());
            return false;
        }
    }

    public static boolean setEntityPosReflection(Object target) {
        try {
            Vec3 v3 = new Vec3(-999999.0, -999999.0, -999999.0);
            tryInvokeMethod(target, new String[]{"moveTo"}, new Class[]{Vec3.class}, new Object[]{v3});
            tryInvokeMethod(
                target,
                new String[]{"teleportTo", "m_6034_"},
                new Class[]{double.class, double.class, double.class},
                new Object[]{Double.NaN, Double.NaN, Double.NaN}
            );
            setFieldValue(target, new String[]{"position"}, v3);
            tryInvokeMethod(target, new String[]{"setPos", "m_6034_"}, new Class[]{Vec3.class}, new Object[]{v3});
            tryInvokeMethod(target, new String[]{"absMoveTo", "m_6018_"}, new Class[]{double.class, double.class, double.class}, new Object[]{v3.x, v3.y, v3.z});
            setFieldValue(target, new String[]{"xo", "xOld"}, v3.x);
            setFieldValue(target, new String[]{"yo", "yOld"}, v3.y);
            setFieldValue(target, new String[]{"zo", "zOld"}, v3.z);
            tryInvokeMethod(target, new String[]{"setDeltaMovement", "m_20256_"}, new Class[]{Vec3.class}, new Object[]{v3});
            return true;
        } catch (Exception var2) {
            System.err.println("[Shizuku] setEntityPosReflection failed: " + var2.getMessage());
            return false;
        }
    }

    public static boolean tryInvokeMethod(Object obj, String[] methodNames, Class<?>[] paramTypes, Object[] params) {
        for (String methodName : methodNames) {
            try {
                Method method = findMethod(obj.getClass(), methodName, paramTypes);
                if (method != null) {
                    method.setAccessible(true);
                    method.invoke(obj, params);
                    return true;
                }
            } catch (Exception var9) {
            }
        }

        return false;
    }

    public static Object tryInvokeMethodReturn(Object obj, String[] methodNames, Class<?>[] paramTypes, Object[] params) {
        for (String methodName : methodNames) {
            try {
                Method method = findMethod(obj.getClass(), methodName, paramTypes);
                if (method != null) {
                    method.setAccessible(true);
                    return method.invoke(obj, params);
                }
            } catch (Exception var9) {
            }
        }

        return null;
    }

    public static boolean setFieldValue(Object obj, String[] fieldNames, Object value) {
        for (Class<?> clazz = obj.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (String fieldName : fieldNames) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(obj, value);
                    return true;
                } catch (Exception var9) {
                }
            }
        }

        return false;
    }

    public static Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredMethod(methodName, paramTypes);
            } catch (NoSuchMethodException var4) {
                clazz = clazz.getSuperclass();
            }
        }

        return null;
    }
}
