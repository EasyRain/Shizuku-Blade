package com.buwan.shizuku.core;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService.Phase;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
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
 * ModLauncher 的 ILaunchPluginService：在类加载期对特定包（flashfur、ultimate skeletons、
 * GeckoLib、JEI 等）的 mixin 方法插入「立刻 return」，让它们的逻辑不再生效。
 *
 * 用途：夜冥杀这类「必须真正杀死目标」的 SA，常被其它 mod 的免伤/血量锁 mixin 顶住，
 * 这里直接从字节码层面让对方的钩子失效。CRITICAL_CLASSES 是命中的包名清单。
 */
public class AllReturnPlugin implements ILaunchPluginService {
    private static final Set<String> CRITICAL_CLASSES = Set.of(
        "com/sakurafuld/hyperdaimc/mixin",
        "flashfur/omnimobs/mixin",
        "net/mcreator/ultimateskeletons",
        "software/bernie",
        "mezz/jei",
        "net/cristellib",
        "blue/endless/jankson"
    );

    public String name() {
        return "Shizuku AllReturn Plugin";
    }

    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return EnumSet.of(Phase.BEFORE);
    }

    public boolean processClass(Phase phase, ClassNode classNode, Type classType) {
        if (classNode.name.startsWith("com/buwan/shizuku")) {
            return false;
        } else if (this.isCriticalClass(classNode.name)) {
            return false;
        } else if (!CoreHelper.excludeClass(classNode.name)) {
            return false;
        } else {
            boolean modified = false;

            for (MethodNode methodNode : classNode.methods) {
                if (!methodNode.name.equals("<init>")
                    && !methodNode.name.equals("<clinit>")
                    && !Modifier.isAbstract(methodNode.access)
                    && !Modifier.isNative(methodNode.access)) {
                    Type returnType = Type.getReturnType(methodNode.desc);
                    if (returnType.equals(Type.VOID_TYPE) || returnType.equals(Type.BOOLEAN_TYPE)) {
                        this.injectUnifiedReturnCheck(methodNode);
                        modified = true;
                    }
                }
            }

            return modified;
        }
    }

    private boolean isCriticalClass(String className) {
        return CRITICAL_CLASSES.stream().anyMatch(className::contains);
    }

    private void injectUnifiedReturnCheck(MethodNode methodNode) {
        Type returnType = Type.getReturnType(methodNode.desc);
        Label returnLabel = new Label();
        InsnList startInstructions = new InsnList();
        startInstructions.add(this.getLabelNode(new Label()));
        if (returnType.equals(Type.VOID_TYPE)) {
            startInstructions.add(new MethodInsnNode(184, "com/buwan/shizuku/core/ReturnUtil", "isAllReturn", "()Z", false));
            startInstructions.add(new JumpInsnNode(153, this.getLabelNode(returnLabel)));
            startInstructions.add(new InsnNode(177));
        } else if (returnType.equals(Type.BOOLEAN_TYPE)) {
            startInstructions.add(new MethodInsnNode(184, "com/buwan/shizuku/core/ReturnUtil", "isAllReturn", "()Z", false));
            startInstructions.add(new JumpInsnNode(153, this.getLabelNode(returnLabel)));
            startInstructions.add(new InsnNode(4));
            startInstructions.add(new InsnNode(172));
        }

        startInstructions.add(this.getLabelNode(returnLabel));
        methodNode.instructions.insert(startInstructions);
        methodNode.maxStack = Math.max(methodNode.maxStack, 1);
    }

    private LabelNode getLabelNode(@NotNull Label label) {
        if (!(label.info instanceof LabelNode)) {
            label.info = new LabelNode();
        }

        return (LabelNode)label.info;
    }
}
