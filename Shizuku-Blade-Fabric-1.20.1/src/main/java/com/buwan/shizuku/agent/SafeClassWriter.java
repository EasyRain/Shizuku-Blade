package com.buwan.shizuku.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * 带自定义 ClassLoader 的 ClassWriter：避免 ASM 在计算栈帧时把本 mod 的类
 * 错误地解析到系统类加载器里（否则会触发 LinkageError / 类泄漏）。
 */
public class SafeClassWriter extends ClassWriter {
    private final ClassLoader classLoader;

    public SafeClassWriter(ClassReader classReader, int flags, ClassLoader classLoader) {
        super(classReader, flags);
        this.classLoader = classLoader;
    }

    protected String getCommonSuperClass(String type1, String type2) {
        try {
            ClassLoader loader = this.classLoader != null ? this.classLoader : ClassLoader.getSystemClassLoader();
            Class<?> c1 = Class.forName(type1.replace('/', '.'), false, loader);
            Class<?> c2 = Class.forName(type2.replace('/', '.'), false, loader);
            if (c1.isAssignableFrom(c2)) {
                return type1;
            } else if (c2.isAssignableFrom(c1)) {
                return type2;
            } else if (!c1.isInterface() && !c2.isInterface()) {
                do {
                    c1 = c1.getSuperclass();
                } while (!c1.isAssignableFrom(c2));

                return c1.getName().replace('.', '/');
            } else {
                return "java/lang/Object";
            }
        } catch (ClassNotFoundException var6) {
            return "java/lang/Object";
        }
    }
}
