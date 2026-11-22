package com.buwan.shizuku.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Inspect the actual injected calls after Mixin has resolved the installed optional mod's methods. */
public final class JudgmentMixinAudit implements IMixinConfigPlugin {
    public void onLoad(String pkg) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) { return true; }
    public void acceptTargets(Set<String> ours, Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.endsWith(".JudgmentDefenseMixin")) return;
        boolean spawn = false, defense = false;
        for (var method : node.methods) {
            for (var insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(node.name)) {
                    spawn |= call.name.contains("shizuku$spawnOnce");
                    defense |= call.name.contains("shizuku$limitDefense");
                }
            }
        }
        var log = LoggerFactory.getLogger("shizuku/judgment");
        if (spawn && defense) log.info("Encounter hooks applied to {} (summon and defense)", target);
        else log.warn("Incomplete encounter hooks on {}: summon={}, defense={}", target, spawn, defense);
    }
}
