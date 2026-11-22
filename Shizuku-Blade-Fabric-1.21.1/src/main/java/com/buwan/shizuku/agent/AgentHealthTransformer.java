package com.buwan.shizuku.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

/**
 * ClassFileTransformer：把 getHealth / getMaxHealth 的字节码换成返回本 mod 缓存的血量值。
 *
 * 匹配的方法名同时考虑了三种命名（ Mojang 名 / 混淆名 / SRG 名）：
 * "getHealth"/"ssGetHealth"/"m_21223_"、"getMaxHealth"/"m_21233_"，以兼容不同运行时。
 * 真实血量由 AgentHealthMethodHelper 写入静态缓存，这里只做取值替换。
 */
public class AgentHealthTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (AgentHelp.blackClass(className)) {
            return null;
        } else {
            try {
                ClassReader cr = new ClassReader(classfileBuffer);
                ClassWriter cw = new SafeClassWriter(cr, 1, loader);
                ClassVisitor cv = new AgentHealthTransformer.AgentHealthClassVisitor(589824, cw);
                cr.accept(cv, 4);
                return cw.toByteArray();
            } catch (Exception var9) {
                System.err.println("[Shizuku] \u8f6c\u6362\u7c7b\u5931\u8d25: " + className);
                var9.printStackTrace();
                return null;
            }
        }
    }

    static class AgentHealthClassVisitor extends ClassVisitor {
        public AgentHealthClassVisitor(int api, ClassVisitor cv) {
            super(api, cv);
        }

        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if ((name.equals("getHealth") || name.equals("ssGetHealth") || name.equals("m_21223_")) && descriptor.equals("()F")) {
                return new AgentHealthTransformer.AgentHealthClassVisitor.HealthMethodVisitor(this.api, mv, true);
            } else {
                return (MethodVisitor)((name.equals("getMaxHealth") || name.equals("m_21233_")) && descriptor.equals("()F")
                    ? new AgentHealthTransformer.AgentHealthClassVisitor.HealthMethodVisitor(this.api, mv, false)
                    : mv);
            }
        }

        static class HealthMethodVisitor extends MethodVisitor {
            private final boolean isGetHealth;

            public HealthMethodVisitor(int api, MethodVisitor mv, boolean isGetHealth) {
                super(api, mv);
                this.isGetHealth = isGetHealth;
            }

            public void visitCode() {
                String helperClass = "com/buwan/shizuku/core/AgentHealthMethodHelper";
                String helperMethod = this.isGetHealth ? "agentGetHealth" : "agentGetMaxHealth";
                Label executeOriginal = new Label();
                this.mv.visitVarInsn(25, 0);
                this.mv.visitMethodInsn(184, helperClass, helperMethod, "(Ljava/lang/Object;)F", false);
                this.mv.visitLdcInsn(-1.0F);
                this.mv.visitInsn(149);
                this.mv.visitJumpInsn(156, executeOriginal);
                this.mv.visitVarInsn(25, 0);
                this.mv.visitMethodInsn(184, helperClass, helperMethod, "(Ljava/lang/Object;)F", false);
                this.mv.visitInsn(174);
                this.mv.visitLabel(executeOriginal);
                super.visitCode();
            }
        }
    }
}
