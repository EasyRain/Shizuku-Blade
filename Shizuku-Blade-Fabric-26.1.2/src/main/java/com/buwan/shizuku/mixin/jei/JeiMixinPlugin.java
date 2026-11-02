package com.buwan.shizuku.mixin.jei;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * JEI mixin 的后手：只在装了 JEI 时应用。
 *
 * <p><b>这里只能用元数据查询</b>（{@code isModLoaded}），绝不能 {@code Class.forName} 任何 JEI 类 ——
 * 该插件运行在 mixin 的 prepare 阶段，此时加载引用 MC 类的 mod 类会导致别的 mod 的 accessor
 * 目标"loaded too early"，直接崩在启动（1.20.1 上踩过）。</p>
 */
public final class JeiMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return FabricLoader.getInstance().isModLoaded("jei");
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
