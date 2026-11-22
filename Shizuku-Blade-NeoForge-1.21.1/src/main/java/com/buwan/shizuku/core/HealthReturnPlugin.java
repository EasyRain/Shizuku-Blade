package com.buwan.shizuku.core;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService.Phase;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * ILaunchPluginService：把若干 mod 的 mixin 中对 getHealth/getMaxHealth 的读写
 * 改写成直接返回本 mod 缓存值，等价于「在字节码层把血量来源换掉」。
 *
 * 与 AllReturnPlugin 的区别：这个只动血量读取，不影响其它逻辑。
 */
public class HealthReturnPlugin implements ILaunchPluginService {
    public String name() {
        return "shizuku_HealthReturn";
    }

    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return EnumSet.of(Phase.BEFORE);
    }

    public boolean processClass(Phase phase, ClassNode classNode, Type classType) {
        if (classNode.name.startsWith("com/buwan/shizuku")) {
            return false;
        } else if (!this.shouldProcessClass(classNode.name)) {
            return false;
        } else {
            boolean modified = false;

            for (MethodNode methodNode : classNode.methods) {
                if (this.shouldModifyMethod(methodNode)) {
                    Type returnType = Type.getReturnType(methodNode.desc);
                    if (returnType.equals(Type.INT_TYPE)) {
                        this.injectIntReturn(methodNode);
                        modified = true;
                    } else if (returnType.equals(Type.FLOAT_TYPE)) {
                        this.injectFloatReturn(methodNode);
                        modified = true;
                    } else if (returnType.equals(Type.DOUBLE_TYPE)) {
                        this.injectDoubleReturn(methodNode);
                        modified = true;
                    }
                }
            }

            return modified;
        }
    }

    private boolean shouldProcessClass(String className) {
        if (className.startsWith("java/")
            || className.startsWith("javax/")
            || className.startsWith("sun/")
            || className.startsWith("com/sun/")
            || className.startsWith("jdk/")) {
            return false;
        } else if (className.startsWith("net/minecraft/") || className.startsWith("com/mojang/")) {
            return false;
        } else if (className.startsWith("net/minecraftforge/") || className.startsWith("cpw/mods/")) {
            return false;
        } else if (className.startsWith("com/google/") || className.startsWith("org/spongepowered/") || className.startsWith("org/objectweb/asm/")) {
            return false;
        } else {
            return className.contains("java/lang/reflect/") || className.contains("ClassLoader") || className.contains("Unsafe")
                ? false
                : !className.startsWith("cpw/mods/modlauncher/");
        }
    }

    private boolean shouldModifyMethod(MethodNode methodNode) {
        return !methodNode.name.equals("<init>")
            && !methodNode.name.equals("<clinit>")
            && !Modifier.isAbstract(methodNode.access)
            && !Modifier.isNative(methodNode.access);
    }

    private void injectIntReturn(MethodNode methodNode) {
        Label skipLabel = new Label();
        LabelNode skipLabelNode = this.createLabelNode(skipLabel);
        InsnList instructions = new InsnList();
        instructions.add(new MethodInsnNode(184, "com/buwan/shizuku/core/HealthReturnSwitch", "isEnabled", "()Z", false));
        instructions.add(new JumpInsnNode(153, skipLabelNode));
        instructions.add(new InsnNode(3));
        instructions.add(new InsnNode(172));
        instructions.add(skipLabelNode);
        methodNode.instructions.insert(instructions);
        methodNode.maxStack = Math.max(methodNode.maxStack + 1, 2);
    }

    private void injectFloatReturn(MethodNode methodNode) {
        Label skipLabel = new Label();
        LabelNode skipLabelNode = this.createLabelNode(skipLabel);
        InsnList instructions = new InsnList();
        instructions.add(new MethodInsnNode(184, "com/buwan/shizuku/core/HealthReturnSwitch", "isEnabled", "()Z", false));
        instructions.add(new JumpInsnNode(153, skipLabelNode));
        instructions.add(new InsnNode(11));
        instructions.add(new InsnNode(174));
        instructions.add(skipLabelNode);
        methodNode.instructions.insert(instructions);
        methodNode.maxStack = Math.max(methodNode.maxStack + 1, 2);
    }

    private void injectDoubleReturn(MethodNode methodNode) {
        Label skipLabel = new Label();
        LabelNode skipLabelNode = this.createLabelNode(skipLabel);
        InsnList instructions = new InsnList();
        instructions.add(new MethodInsnNode(184, "com/buwan/shizuku/core/HealthReturnSwitch", "isEnabled", "()Z", false));
        instructions.add(new JumpInsnNode(153, skipLabelNode));
        instructions.add(new InsnNode(14));
        instructions.add(new InsnNode(175));
        instructions.add(skipLabelNode);
        methodNode.instructions.insert(instructions);
        methodNode.maxStack = Math.max(methodNode.maxStack + 2, 3);
    }

    private LabelNode createLabelNode(Label label) {
        if (!(label.info instanceof LabelNode)) {
            label.info = new LabelNode();
        }

        return (LabelNode)label.info;
    }
}
