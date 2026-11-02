package com.buwan.shizuku.mixin.jei;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * JEI mixin 的"后手"：
 *
 * <ol>
 *   <li>没装 JEI → 整个配置不应用（目标类不存在，直接注册会崩）；</li>
 *   <li>装了 JEI 但目标方法签名变了（JEI 破坏性更新）→ 通过反射探测方法是否还在，
 *       不在就跳过，游戏照常跑，tooltip 退化为原版样式；</li>
 *   <li>配置本身 {@code required: false} + {@code defaultRequire: 0} →
 *       即便注入时仍然失败，也只记录一条日志，不会让游戏崩溃。</li>
 * </ol>
 *
 * <p>换句话说：这个功能只能"锦上添花"，任何情况下都不应该把游戏带崩。</p>
 */
public final class JeiMixinPlugin implements IMixinConfigPlugin {

    private static final String TARGET = "mezz.jei.fabric.platform.RenderHelper";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!FabricLoader.getInstance().isModLoaded("jei")) {
            return false;
        }
        return hasExpectedTooltipMethods();
    }

    /**
     * 探测 {@code RenderHelper#renderTooltip} 是否还是我们认识的样子（6 或 7 个参数）。
     * {@code initialize = false} 不会触发 JEI 类的静态初始化。
     */
    private static boolean hasExpectedTooltipMethods() {
        try {
            Class<?> target = Class.forName(TARGET, false, JeiMixinPlugin.class.getClassLoader());
            for (Method method : target.getDeclaredMethods()) {
                if (!"renderTooltip".equals(method.getName())) {
                    continue;
                }
                int count = method.getParameterCount();
                if (count == 6 || count == 7) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // JEI 结构变化 / 类加载失败：一律按"不支持"处理
        }
        return false;
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
