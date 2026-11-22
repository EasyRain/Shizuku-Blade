package com.buwan.shizuku.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

/**
 * 反射总工具箱：按名取字段/方法、按类型找字段、Unsafe 强制写 final 字段、
 * 以及黑名单类判断（blackClass）。
 *
 * 本 mod 里所有「改不动就反射」的逻辑都收敛在这里，便于一处维护。
 */
public class CoreHelper {
    private static Unsafe UNSAFE = getUnsafe();

    private static Unsafe getUnsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe)theUnsafe.get(null);
        } catch (Exception var3) {
            try {
                Constructor<Unsafe> unsafeConstructor = Unsafe.class.getDeclaredConstructor();
                unsafeConstructor.setAccessible(true);
                return unsafeConstructor.newInstance();
            } catch (Exception var2) {
                System.err.println("Could not get Unsafe instance: " + var2.getMessage());
                return null;
            }
        }
    }

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

    public static Field getField(Class<?> clazz, String name) {
        try {
            return clazz.getDeclaredField(name);
        } catch (Exception var6) {
            for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException var5) {
                }
            }

            System.err.println("Field not found: " + name + " in " + clazz.getName());
            return null;
        }
    }

    public static <T> T getFieldValueByField(Field f, Object target, Class<T> clazz) {
        if (f == null) {
            return null;
        } else {
            try {
                Object result;
                if (UNSAFE == null) {
                    f.setAccessible(true);
                    result = f.get(target);
                } else {
                    long offset = 0L;
                    if (Modifier.isStatic(f.getModifiers())) {
                        target = UNSAFE.staticFieldBase(f);
                        offset = UNSAFE.staticFieldOffset(f);
                    } else {
                        offset = UNSAFE.objectFieldOffset(f);
                    }

                    result = UNSAFE.getObject(target, offset);
                }

                return clazz.cast(result);
            } catch (Throwable var7) {
                try {
                    f.setAccessible(true);
                    Object result = f.get(target);
                    return clazz.cast(result);
                } catch (Exception var6) {
                    System.err.println("Failed to get field value: " + f.getName());
                    return null;
                }
            }
        }
    }

    public static <T> T getFieldValueByName(Object target, String fieldName, Class<T> clazz) {
        try {
            Field field = getField(target.getClass(), fieldName);
            return getFieldValueByField(field, target, clazz);
        } catch (Throwable var4) {
            System.err.println("Failed to get field value: " + fieldName);
            return null;
        }
    }

    public static <T> T getFieldValue(Class<?> target, String fieldName, Class<T> clazz) {
        try {
            Field field = getField(target, fieldName);
            return getFieldValueByField(field, null, clazz);
        } catch (Throwable var4) {
            System.err.println("Failed to get static field value: " + fieldName);
            return null;
        }
    }

    public static void setFieldValue(Object target, String fieldName, Object value) {
        try {
            Field field = getField(target.getClass(), fieldName);
            setFieldValue(field, target, value);
        } catch (Throwable var4) {
            System.err.println("Failed to set field value: " + fieldName);
        }
    }

    public static void setFieldValue(Field f, Object target, Object value) {
        if (f != null) {
            try {
                if (UNSAFE == null) {
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                }

                long offset = 0L;
                if (Modifier.isStatic(f.getModifiers())) {
                    target = UNSAFE.staticFieldBase(f);
                    offset = UNSAFE.staticFieldOffset(f);
                } else {
                    offset = UNSAFE.objectFieldOffset(f);
                }

                UNSAFE.putObject(target, offset, value);
            } catch (Throwable var6) {
                try {
                    f.setAccessible(true);
                    f.set(target, value);
                } catch (Exception var5) {
                    System.err.println("Failed to set field value: " + f.getName());
                }
            }
        }
    }

    public static boolean excludeClass(@NotNull String name) {
        if (name.startsWith("net/minecraft") || name.startsWith("net/minecraftforge") || name.startsWith("com/mojang") || name.startsWith("cpw/mods")) {
            return false;
        } else if (name.startsWith("com/google")
            || name.startsWith("org/objectweb")
            || name.startsWith("org/spongepowered")
            || name.startsWith("java")
            || name.startsWith("javax")
            || name.startsWith("sun")
            || name.startsWith("com/sun")
            || name.startsWith("org/joml")
            || name.startsWith("org/apache")
            || name.startsWith("it/unimi")
            || name.startsWith("org/slf4j")
            || name.startsWith("org/lwjgl")) {
            return false;
        } else if (name.startsWith("software/bernie") || name.startsWith("mezz/jei")) {
            return false;
        } else if (name.contains("init")
            || name.contains("Init")
            || name.contains("Creative")
            || name.contains("creative")
            || name.contains("registry")
            || name.contains("Registry")
            || name.contains("config")
            || name.contains("Config")) {
            return false;
        } else if (name.contains("spawn")
            || name.contains("Spawn")
            || name.contains("generation")
            || name.contains("Generation")
            || name.contains("generate")
            || name.contains("Generate")
            || name.contains("populate")
            || name.contains("Populate")) {
            return false;
        } else if (name.contains("egg")
            || name.contains("Egg")
            || name.contains("world")
            || name.contains("World")
            || name.contains("biome")
            || name.contains("Biome")) {
            return false;
        } else if (name.contains("feature")
            || name.contains("Feature")
            || name.contains("structure")
            || name.contains("Structure")
            || name.contains("network")
            || name.contains("Network")
            || name.contains("packet")
            || name.contains("Packet")) {
            return false;
        } else if (name.contains("syncher")
            || name.contains("Syncher")
            || name.contains("data")
            || name.contains("Data")
            || name.contains("chunk")
            || name.contains("Chunk")
            || name.contains("event")
            || name.contains("Event")) {
            return false;
        } else {
            return name.contains("client")
                    || name.contains("Client")
                    || name.contains("block")
                    || name.contains("Block")
                    || name.contains("goal")
                    || name.contains("Goal")
                    || name.contains("behavior")
                    || name.contains("Behavior")
                ? false
                : !name.contains("command") && !name.contains("Command") && !name.contains("recipe") && !name.contains("Recipe");
        }
    }
}
