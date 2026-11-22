package com.buwan.shizuku.sa;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

public final class SuperSteveCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation BOSS = ResourceLocation.fromNamespaceAndPath("supersteve", "super_steve");

    private SuperSteveCompat() {}

    public static boolean isAvailable() {
        return Hook.KILL != null;
    }

    public static boolean isBoss(LivingEntity entity) {
        return BOSS.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
    }

    public static boolean execute(LivingEntity entity) {
        MethodHandle kill = Hook.KILL;
        if (kill == null || !isBoss(entity) || entity.level().isClientSide()) {
            return false;
        }
        try {
            kill.invokeExact((Entity) entity, true);
            boolean defeated = !entity.isAlive();
            LOGGER.info("Judgment resolved SuperSteve {} with alive = {}", entity.getUUID(), !defeated);
            return defeated;
        } catch (Throwable e) {
            if (e instanceof Error error && !(error instanceof LinkageError)) {
                throw error;
            }
            LOGGER.warn("SuperSteve Judgment integration could not execute {}", entity.getUUID(), e);
            return false;
        }
    }

    private static final class Hook {
        private static final MethodHandle KILL = resolve();

        private static MethodHandle resolve() {
            if (!ModList.get().isLoaded(BOSS.getNamespace())) {
                return null;
            }
            try {
                Class<?> api = Class.forName("plz.lizi.supersteve.api.SSUtil", false, SuperSteveCompat.class.getClassLoader());
                return MethodHandles.publicLookup().findStatic(api, "killEntity", MethodType.methodType(void.class, Entity.class, boolean.class));
            } catch (ReflectiveOperationException | LinkageError e) {
                LOGGER.warn("SuperSteve is loaded but its Judgment integration is unavailable", e);
                return null;
            }
        }
    }
}
