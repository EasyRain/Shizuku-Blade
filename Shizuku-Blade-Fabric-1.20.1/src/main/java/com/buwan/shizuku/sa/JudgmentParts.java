package com.buwan.shizuku.sa;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.entity.PartEntity;

final class JudgmentParts {
    private static final TagKey<EntityType<?>> LINKED_PARTS = TagKey.create(Registries.ENTITY_TYPE,
        new ResourceLocation("shizuku", "judgment_linked_parts"));
    private static final ClassValue<Optional<MethodHandle>> PARENT = new ClassValue<>() {
        @Override protected Optional<MethodHandle> computeValue(Class<?> type) {
            try {
                var method = type.getMethod("getParent");
                if (!Entity.class.isAssignableFrom(method.getReturnType())
                        || java.lang.reflect.Modifier.isStatic(method.getModifiers())) return Optional.empty();
                return Optional.of(MethodHandles.publicLookup().unreflect(method)
                    .asType(MethodType.methodType(Entity.class, Entity.class)));
            } catch (ReflectiveOperationException e) {
                return Optional.empty();
            }
        }
    };

    private JudgmentParts() {}

    static Entity root(Entity hit) {
        Entity current = hit;
        Set<Entity> visited = null;
        while (current instanceof PartEntity<?> || current.getType().is(LINKED_PARTS)) {
            if (visited == null) visited = new HashSet<>();
            if (!visited.add(current)) return hit;
            Entity parent;
            if (current instanceof PartEntity<?> part) parent = part.getParent();
            else {
                var handle = PARENT.get(current.getClass());
                if (handle.isEmpty()) return current;
                try {
                    parent = (Entity)handle.get().invokeExact(current);
                } catch (Error e) {
                    throw e;
                } catch (Throwable e) {
                    return current;
                }
            }
            if (parent == null || parent.level() != hit.level() || parent.isRemoved()) return current;
            current = parent;
        }
        return current;
    }
}
