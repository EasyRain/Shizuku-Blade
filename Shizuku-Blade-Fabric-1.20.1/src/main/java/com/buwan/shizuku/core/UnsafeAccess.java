package com.buwan.shizuku.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * sun.misc.Unsafe 的反射封装。
 *
 * 为什么不用普通反射：Java 17 起 java.base 里 MC 的核心类字段全是强封装，
 * {@link Field#setAccessible(boolean)} 会直接抛 InaccessibleObjectException；
 * 而 1.20.1 的 Forge 又没给这些字段打 access transformer。
 * Unsafe 走的是"裸内存偏移"，不看访问修饰符、不看模块边界、也能写 final 字段。
 *
 * 设计要点：
 * - 全反射调用，不 import sun.misc.Unsafe，避免编译期依赖 jdk.unsupported 的导出；
 * - 字段偏移/方法句柄一律缓存，热路径上只有一次 ConcurrentHashMap 查询；
 * - 写入统一走 putXxxVolatile（带内存屏障），这是对抗 JIT 的关键：
 *   普通 putFloat 可能被 C2 提到循环外、或和读取重排，volatile 写强制刷新可见性，
 *   保证"写好血量"这件事对已经 tier-4 编译过的 getHealth 调用点立刻可见；
 * - 拿不到 Unsafe 时全部降级返回 false，调用方自行回退到反射/官方 API。
 */
public final class UnsafeAccess {
    private static final Object UNSAFE;
    private static final Method OBJECT_FIELD_OFFSET;
    private static final Method PUT_INT_VOLATILE;
    private static final Method PUT_LONG_VOLATILE;
    private static final Method PUT_FLOAT_VOLATILE;
    private static final Method PUT_BOOLEAN_VOLATILE;
    private static final Method PUT_OBJECT_VOLATILE;
    private static final Method GET_INT_VOLATILE;
    private static final Method GET_FLOAT_VOLATILE;
    private static final Method GET_OBJECT_VOLATILE;
    private static final Method STORE_FENCE;
    private static final Method FULL_FENCE;

    private static final Map<String, Long> OFFSETS = new ConcurrentHashMap<>();

    static {
        Object unsafe = null;
        Method objectFieldOffset = null;
        Method putIntVolatile = null;
        Method putLongVolatile = null;
        Method putFloatVolatile = null;
        Method putBooleanVolatile = null;
        Method putObjectVolatile = null;
        Method getIntVolatile = null;
        Method getFloatVolatile = null;
        Method getObjectVolatile = null;
        Method storeFence = null;
        Method fullFence = null;
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = type.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = theUnsafe.get(null);
            if (unsafe != null) {
                objectFieldOffset = type.getMethod("objectFieldOffset", Field.class);
                putIntVolatile = type.getMethod("putIntVolatile", Object.class, long.class, int.class);
                putLongVolatile = type.getMethod("putLongVolatile", Object.class, long.class, long.class);
                putFloatVolatile = type.getMethod("putFloatVolatile", Object.class, long.class, float.class);
                putBooleanVolatile = type.getMethod("putBooleanVolatile", Object.class, long.class, boolean.class);
                putObjectVolatile = type.getMethod("putObjectVolatile", Object.class, long.class, Object.class);
                getIntVolatile = type.getMethod("getIntVolatile", Object.class, long.class);
                getFloatVolatile = type.getMethod("getFloatVolatile", Object.class, long.class);
                getObjectVolatile = type.getMethod("getObjectVolatile", Object.class, long.class);
                storeFence = type.getMethod("storeFence");
                fullFence = type.getMethod("fullFence");
            }
        } catch (Throwable ignored) {
            unsafe = null;
        }
        UNSAFE = unsafe;
        OBJECT_FIELD_OFFSET = objectFieldOffset;
        PUT_INT_VOLATILE = putIntVolatile;
        PUT_LONG_VOLATILE = putLongVolatile;
        PUT_FLOAT_VOLATILE = putFloatVolatile;
        PUT_BOOLEAN_VOLATILE = putBooleanVolatile;
        PUT_OBJECT_VOLATILE = putObjectVolatile;
        GET_INT_VOLATILE = getIntVolatile;
        GET_FLOAT_VOLATILE = getFloatVolatile;
        GET_OBJECT_VOLATILE = getObjectVolatile;
        STORE_FENCE = storeFence;
        FULL_FENCE = fullFence;
    }

    private UnsafeAccess() {}

    public static boolean available() {
        return UNSAFE != null;
    }

    /**
     * 解析实例字段的内存偏移。names 支持多个候选名（Mojang 名 / SRG 名 / Forge 名），
     * 沿继承链向上找，命中后缓存。
     */
    public static long offsetOf(Class<?> owner, String... names) {
        if (UNSAFE == null || names.length == 0) return -1L;
        String key = owner.getName() + '#' + String.join("|", names);
        Long cached = OFFSETS.get(key);
        if (cached != null) return cached;
        long offset = -1L;
        for (Class<?> clazz = owner; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    offset = (Long) OBJECT_FIELD_OFFSET.invoke(UNSAFE, field);
                    break;
                } catch (Throwable ignored) {
                }
            }
            if (offset >= 0L) break;
        }
        OFFSETS.put(key, offset);
        return offset;
    }

    public static boolean setInt(Object target, long offset, int value) {
        if (UNSAFE == null || offset < 0L || target == null) return false;
        try {
            PUT_INT_VOLATILE.invoke(UNSAFE, target, offset, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean setLong(Object target, long offset, long value) {
        if (UNSAFE == null || offset < 0L || target == null) return false;
        try {
            PUT_LONG_VOLATILE.invoke(UNSAFE, target, offset, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean setFloat(Object target, long offset, float value) {
        if (UNSAFE == null || offset < 0L || target == null) return false;
        try {
            PUT_FLOAT_VOLATILE.invoke(UNSAFE, target, offset, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean setBoolean(Object target, long offset, boolean value) {
        if (UNSAFE == null || offset < 0L || target == null) return false;
        try {
            PUT_BOOLEAN_VOLATILE.invoke(UNSAFE, target, offset, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean setObject(Object target, long offset, Object value) {
        if (UNSAFE == null || offset < 0L || target == null) return false;
        try {
            PUT_OBJECT_VOLATILE.invoke(UNSAFE, target, offset, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int getInt(Object target, long offset, int fallback) {
        if (UNSAFE == null || offset < 0L || target == null) return fallback;
        try {
            return (Integer) GET_INT_VOLATILE.invoke(UNSAFE, target, offset);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static float getFloat(Object target, long offset, float fallback) {
        if (UNSAFE == null || offset < 0L || target == null) return fallback;
        try {
            return (Float) GET_FLOAT_VOLATILE.invoke(UNSAFE, target, offset);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static Object getObject(Object target, long offset) {
        if (UNSAFE == null || offset < 0L || target == null) return null;
        try {
            return GET_OBJECT_VOLATILE.invoke(UNSAFE, target, offset);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 只保证之前的写对其他线程可见，不解 JIT 的读缓存。 */
    public static void storeFence() {
        if (STORE_FENCE == null) return;
        try {
            STORE_FENCE.invoke(UNSAFE);
        } catch (Throwable ignored) {
        }
    }

    /** 读写全屏障：既刷新写，也让 JIT 丢弃寄存器中的旧值。 */
    public static void fullFence() {
        if (FULL_FENCE == null) return;
        try {
            FULL_FENCE.invoke(UNSAFE);
        } catch (Throwable ignored) {
        }
    }
}
