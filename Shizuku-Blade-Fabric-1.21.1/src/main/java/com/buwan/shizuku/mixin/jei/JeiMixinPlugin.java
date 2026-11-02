package com.buwan.shizuku.mixin.jei;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * JEI mixin 的"后手"。
 *
 * <p><b>重要约束</b>：这里只能用 {@link FabricLoader#isModLoaded(String)} 这类**元数据查询**，
 * 绝对不能 {@code Class.forName} 任何 JEI 类 —— 该插件运行在 mixin 的 <b>prepare 阶段</b>，
 * 一旦在此时加载了会引用 {@code ItemStack} 的类，就会导致
 * SlashBlade 的 {@code accessor.ItemStackAccessor} 目标"loaded too early"
 * 从而启动崩溃（实测踩过）。</p>
 *
 * <p>JEI 改签名时的保险不在这里，而在配置的 {@code injectors.defaultRequire = 0}：
 * 注入失败只记日志，游戏照常运行，tooltip 退化为原版样式。</p>
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
        // 仅元数据判断，不加载任何 JEI / MC 类
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
