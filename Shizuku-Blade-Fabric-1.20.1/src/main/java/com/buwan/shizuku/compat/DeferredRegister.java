package com.buwan.shizuku.compat;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * NeoForge {@code DeferredRegister} 的最小 Fabric 兼容实现：
 * 先收集条目，在 mod 初始化时调用 {@link #registerAll()} 真正注册到 Registry。
 * 这样 NeoForge 时代的注册代码只需改 import，不必重写。
 */
public class DeferredRegister<T> {
    private final Registry<T> registry;
    private final String namespace;
    private final List<Runnable> pending = new ArrayList<>();
    private boolean done;

    public DeferredRegister(Registry<T> registry, String namespace) {
        this.registry = registry;
        this.namespace = namespace;
    }

    public static <T> DeferredRegister<T> create(Registry<T> registry, String namespace) {
        return new DeferredRegister<>(registry, namespace);
    }

    public ResourceLocation id(String name) {
        return new ResourceLocation(namespace, name);
    }

    public <R extends T> DeferredHolder<T, R> register(String name, Supplier<R> supplier) {
        DeferredHolder<T, R> holder = new DeferredHolder<>(registry, id(name), supplier);
        pending.add(holder::registerNow);
        return holder;
    }

    /** 在 {@code ModInitializer#onInitialize} 里调用一次。 */
    public void registerAll() {
        if (done) {
            return;
        }
        done = true;
        for (Runnable r : pending) {
            r.run();
        }
        pending.clear();
    }

    /** 兼容 NeoForge 的 {@code DR.register(eventBus)} 调用：视为立即注册。 */
    public void register(Object ignored) {
        registerAll();
    }
}
