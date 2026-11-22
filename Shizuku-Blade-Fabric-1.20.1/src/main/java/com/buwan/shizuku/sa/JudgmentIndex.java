package com.buwan.shizuku.sa;

import com.buwan.shizuku.mixin.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;

/** Snapshot the loaded server stores once per pass, including sections outside entity-ticking range. */
final class JudgmentIndex {
    final Set<Entity> entities = new LinkedHashSet<>();
    final IdentityHashMap<Entity,List<EntitySection<Entity>>> sections = new IdentityHashMap<>();
    final IdentityHashMap<Entity, List<Entity>> parts = new IdentityHashMap<>();
    final ServerLevel level;
    final ServerLevelAccessor world;
    final EntityManagerAccessor manager;
    final EntityLookupAccessor lookup;
    final EntityTickListAccessor ticking;
    final ChunkMapAccessor chunks;

    JudgmentIndex(ServerLevel level) {
        this.level = level;
        world = (ServerLevelAccessor)level;
        manager = (EntityManagerAccessor)world.ymsa$getEntityManager();
        lookup = (EntityLookupAccessor)manager.ymsa$getVisibleEntityStorage();
        ticking = (EntityTickListAccessor)world.ymsa$getEntityTickList();
        chunks = (ChunkMapAccessor)level.getChunkSource().chunkMap;
        entities.addAll(lookup.ymsa$getByUuid().values());
        entities.addAll(lookup.ymsa$getById().values());
        entities.addAll(ticking.ymsa$getActive().values());
        entities.addAll(level.getPartEntities());
        for (Object entry : chunks.ymsa$getEntityMap().values()) entities.add(((TrackedEntityAccessor)entry).shizuku$entity());
        var storage = manager.ymsa$getSectionStorage();
        for (long chunk : storage.getAllChunksWithExistingSections()) {
            storage.getExistingSectionsInChunk(chunk).forEach(section -> section.getEntities().forEach(entity -> {
                entities.add(entity);
                sections.computeIfAbsent(entity, ignored -> new ArrayList<>(1)).add(section);
            }));
        }
        for (Entity candidate : entities) {
            if (candidate instanceof net.minecraftforge.entity.PartEntity<?> part)
                parts.computeIfAbsent(part.getParent(), ignored -> new ArrayList<>()).add(part);
        }
    }

    Set<Entity> verdictTargets(AABB area, UUID caster) {
        Set<Entity> roots = new LinkedHashSet<>();
        for (Entity hit : entities) {
            if (!area.intersects(hit.getBoundingBox()) && !area.contains(hit.position())) continue;
            Entity root = JudgmentParts.root(hit);
            if (root instanceof Player || root instanceof OwnableEntity pet && caster.equals(pet.getOwnerUUID())) continue;
            roots.add(root);
            // Some mods register each segment as a separate living entity with its own lifecycle.
            if (root != hit && !(hit instanceof PartEntity<?>)) roots.add(hit);
        }
        return roots;
    }

    boolean detach(Entity entity) {
        int id = entity.getId();
        var uuid = entity.getUUID();
        lookup.ymsa$getById().remove(id, entity);
        lookup.ymsa$getByUuid().remove(uuid, entity);
        Entity uuidOccupant = lookup.ymsa$getByUuid().get(uuid);
        if (uuidOccupant == null) manager.ymsa$getKnownUuids().remove(uuid);
        var locations = sections.getOrDefault(entity, List.of());
        for (var section : locations) clearSection(section, entity);
        var current = manager.ymsa$getSectionStorage().getSection(net.minecraft.core.SectionPos.asLong(entity.blockPosition()));
        if (current != null && !locations.contains(current)) clearSection(current, entity);
        if (ticking.ymsa$getActive().get(id) == entity) world.ymsa$getEntityTickList().remove(entity);
        ticking.ymsa$getActive().remove(id, entity);
        ticking.ymsa$getPassive().remove(id, entity);
        world.ymsa$getNavigatingMobs().remove(entity);
        if (tracks(entity)) level.getChunkSource().removeEntity(entity);
        Object remainingTracker = chunks.ymsa$getEntityMap().get(id);
        if (remainingTracker instanceof TrackedEntityAccessor record && record.shizuku$entity() == entity)
            chunks.ymsa$getEntityMap().remove(id, remainingTracker);
        return lookup.ymsa$getById().get(id) != entity && lookup.ymsa$getByUuid().get(uuid) != entity
            && (uuidOccupant != null || !manager.ymsa$getKnownUuids().contains(uuid))
            && ticking.ymsa$getActive().get(id) != entity && ticking.ymsa$getPassive().get(id) != entity
            && !tracks(entity) && (current == null || current.getEntities().noneMatch(e -> e == entity))
            && locations.stream().noneMatch(s -> s.getEntities().anyMatch(e -> e == entity));
    }

    private static void clearSection(EntitySection<Entity> section, Entity target) {
        section.remove(target);
        var storage = (ClassInstanceMultiMapAccessor)((EntitySectionAccessor)section).ymsa$getStorage();
        for (var bucket : storage.ymsa$getByClass().values()) {
            bucket.removeIf(entity -> entity == target);
        }
    }

    private boolean tracks(Entity entity) {
        Object entry = chunks.ymsa$getEntityMap().get(entity.getId());
        return entry instanceof TrackedEntityAccessor tracker && tracker.shizuku$entity() == entity;
    }
}
