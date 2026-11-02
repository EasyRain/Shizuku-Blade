package com.buwan.shizuku.compat;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * NeoForge {@code DeferredHolder} 的最小 Fabric 兼容实现。
 * 真正注册发生在地 {@link com.buwan.shizuku.compat.DeferredRegister#registerAll()} 时。
 */
public class DeferredHolder<B, T extends B> implements Supplier<T> {
    private final Registry<B> registry;
    private final ResourceLocation id;
    private final Supplier<? extends T> supplier;
    private T value;

    public DeferredHolder(Registry<B> registry, ResourceLocation id, Supplier<? extends T> supplier) {
        this.registry = registry;
        this.id = id;
        this.supplier = supplier;
    }

    public void registerNow() {
        if (value == null) {
            value = Registry.register(registry, id, supplier.get());
        }
    }

    @Override
    public T get() {
        if (value == null) {
            registerNow();
        }
        return value;
    }

    public ResourceLocation getId() {
        return id;
    }
}
