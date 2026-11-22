package com.buwan.shizuku.sa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Server-thread only. Overworld game time is shared by all encounter dimensions. */
final class UnleashedVerdicts extends SavedData {
    private record Target(UUID caster, UUID uuid, ResourceLocation dimension, ResourceLocation type,
                          Vec3 position, long start, boolean boss) {}
    private final Map<UUID, List<Target>> targets = new HashMap<>();
    private final Map<ResourceLocation, List<Target>> encounters = new HashMap<>();

    private void add(Target target) {
        var owners = targets.computeIfAbsent(target.uuid, id -> new ArrayList<>());
        owners.removeIf(t -> t.caster.equals(target.caster));
        owners.add(target);
        if (target.boss) {
            var area = encounters.computeIfAbsent(target.dimension, id -> new ArrayList<>());
            area.removeIf(t -> t.caster.equals(target.caster) && t.uuid.equals(target.uuid));
            area.add(target);
        }
    }

    void includeSimultaneousVerdicts(UUID caster, ServerLevel level, AABB area) {
        long now = level.getServer().overworld().getGameTime();
        // The first sword may already have removed the entity earlier in this server tick.
        for (var owners : targets.values()) {
            if (owners.stream().anyMatch(t -> t.caster.equals(caster))) continue;
            var t = owners.get(0);
            if (t.start == now && t.dimension.equals(level.dimension().location()) && area.contains(t.position)) {
                add(new Target(caster, t.uuid, t.dimension, t.type, t.position, now, t.boss));
                setDirty();
            }
        }
    }

    static UnleashedVerdicts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(UnleashedVerdicts::load,
            UnleashedVerdicts::new, "shizuku_unleashed_verdicts");
    }

    void record(UUID caster, Entity entity, boolean boss) {
        var level = (ServerLevel)entity.level();
        var type = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (type == null) return;
        add(new Target(caster, entity.getUUID(), level.dimension().location(), type,
            entity.position(), level.getServer().overworld().getGameTime(), boss));
        UUID encounter = JudgmentEncounters.link(entity, entity.getPersistentData());
        if (encounter != null) recordController(caster, level, encounter, entity.position());
        if (boss && entity.getPersistentData().hasUUID("shizuku:spawn_source"))
            recordController(caster, level, entity.getPersistentData().getUUID("shizuku:spawn_source"), entity.position());
        setDirty();
    }

    void recordController(UUID caster, ServerLevel level, UUID id, Vec3 location) {
        long now = level.getServer().overworld().getGameTime();
        if (targets.getOrDefault(id, List.of()).stream().noneMatch(t -> t.caster.equals(caster) && t.start == now)) {
            add(new Target(caster, id, level.dimension().location(), new ResourceLocation("shizuku", "controller"), location, now, false));
            setDirty();
        }
    }

    void clearCaster(UUID caster) {
        endCast(caster);
        removeIf(t -> t.caster.equals(caster));
    }

    boolean prune(long now) {
        removeIf(t -> now - t.start >= JudgmentConfig.RETENTION.get());
        return !targets.isEmpty();
    }

    private void removeIf(java.util.function.Predicate<Target> test) {
        boolean changed = false;
        for (var owners : targets.values()) changed |= owners.removeIf(test);
        if (!changed) return;
        targets.values().removeIf(List::isEmpty);
        for (var area : encounters.values()) area.removeIf(test);
        encounters.values().removeIf(List::isEmpty);
        setDirty();
    }

    boolean blocksSpawn(ServerLevel level, ResourceLocation type, Vec3 position) {
        long now = level.getServer().overworld().getGameTime();
        return encounters.getOrDefault(level.dimension().location(), List.of()).stream().anyMatch(t ->
            t.type.equals(type) && now - t.start < JudgmentConfig.RETENTION.get() && t.position.distanceToSqr(position) <= JudgmentConfig.RADIUS.get() * JudgmentConfig.RADIUS.get());
    }

    boolean matches(ServerLevel level, Entity entity) {
        if (targets.isEmpty() || entity instanceof Player) return false;
        if (entity instanceof ItemEntity drop) return JudgmentEncounters.blockedItem(level, drop.getItem());
        UUID owner = null;
        if (entity instanceof PartEntity<?> part) owner = part.getParent().getUUID();
        else if (entity instanceof com.buwan.shizuku.mixin.JudgmentProjectileAccessor projectile) owner = projectile.shizuku$ownerUUID();
        else if (entity instanceof OwnableEntity ownable) owner = ownable.getOwnerUUID();
        else if (entity instanceof TraceableEntity traceable) {
            Entity parent = traceable.getOwner();
            if (parent != null) owner = parent.getUUID();
        }
        long now = level.getServer().overworld().getGameTime();
        if (active(entity.getUUID(), now) || (owner != null && active(owner, now))) return true;
        var tag = entity.getPersistentData();
        UUID encounter = JudgmentEncounters.link(entity, tag);
        if (encounter != null && active(encounter, now)) return true;
        if (tag.hasUUID("shizuku:spawn_source") && active(tag.getUUID("shizuku:spawn_source"), now)) return true;
        if (!(entity instanceof LivingEntity living)) return false;
        var type = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        for (Target t : encounters.getOrDefault(level.dimension().location(), List.of())) {
            long age = now - t.start;
            if (age >= JudgmentConfig.RETENTION.get() || t.caster.equals(owner) || type == null
                    || t.position.distanceToSqr(entity.position()) > JudgmentConfig.RADIUS.get() * JudgmentConfig.RADIUS.get()) continue;
            if (t.type.equals(type) || (age <= JudgmentConfig.PHASE_WINDOW.get()
                    && Ymsa.isUnleashedBoss(living))) return true;
        }
        return false;
    }

    boolean active(UUID id, long now) {
        var owners = targets.get(id);
        return owners != null && owners.stream().anyMatch(t -> now - t.start < JudgmentConfig.RETENTION.get());
    }

    boolean hasLoadedMatch(ServerLevel level) {
        var world = (com.buwan.shizuku.mixin.ServerLevelAccessor)level;
        var manager = (com.buwan.shizuku.mixin.EntityManagerAccessor)world.ymsa$getEntityManager();
        var lookup = (com.buwan.shizuku.mixin.EntityLookupAccessor)manager.ymsa$getVisibleEntityStorage();
        for (Entity entity : lookup.ymsa$getById().values()) if (matches(level, entity)) return true;
        for (Entity entity : lookup.ymsa$getByUuid().values()) if (matches(level, entity)) return true;
        var ticking = (com.buwan.shizuku.mixin.EntityTickListAccessor)world.ymsa$getEntityTickList();
        for (Entity entity : ticking.ymsa$getActive().values()) if (matches(level, entity)) return true;
        var chunks = (com.buwan.shizuku.mixin.ChunkMapAccessor)level.getChunkSource().chunkMap;
        for (Object record : chunks.ymsa$getEntityMap().values())
            if (matches(level, ((com.buwan.shizuku.mixin.TrackedEntityAccessor)record).shizuku$entity())) return true;
        for (Entity part : level.getPartEntities()) if (matches(level, part)) return true;
        var storage = manager.ymsa$getSectionStorage();
        for (long chunk : storage.getAllChunksWithExistingSections()) {
            if (storage.getExistingSectionsInChunk(chunk).flatMap(section -> section.getEntities())
                    .anyMatch(entity -> matches(level, entity))) return true;
        }
        return false;
    }

    private CompoundTag casts = new CompoundTag();

    void beginCast(UUID caster, UUID blade) {
        var cast = new CompoundTag();
        cast.putUUID("Blade", blade);
        casts.put(caster.toString(), cast);
        setDirty();
    }

    void endCast(UUID caster) {
        if (casts.contains(caster.toString())) {
            casts.remove(caster.toString());
            setDirty();
        }
    }

    int savedAge(UUID caster, UUID blade) {
        var cast = casts.getCompound(caster.toString());
        return cast.hasUUID("Blade") && cast.getUUID("Blade").equals(blade) ? cast.getInt("Age") : -1;
    }

    boolean advance(UUID caster, int age) {
        var cast = casts.getCompound(caster.toString());
        if (!cast.hasUUID("Blade") || age <= cast.getInt("Age")) return false;
        cast.putInt("Age", age);
        setDirty();
        return true;
    }

    void lockTargets(net.minecraft.server.level.ServerPlayer player) {
        var cast = casts.getCompound(player.getStringUUID());
        var list = new ListTag();
        var area = player.getBoundingBox().inflate(320);
        for (Entity entity : new JudgmentIndex(player.serverLevel()).verdictTargets(area, player.getUUID())) {
            if (!(entity instanceof LivingEntity living ? Ymsa.isUnleashedBoss(living) : JudgmentEncounters.identifies(entity))) continue;
            appendLock(list, entity);
        }
        cast.put("Locked", list);
        setDirty();
    }

    private static void appendLock(ListTag list, Entity entity) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null) return;
        var tag = new CompoundTag();
        tag.putUUID("UUID", entity.getUUID());
        tag.putString("Type", key.toString());
        tag.putString("Dimension", entity.level().dimension().location().toString());
        tag.putDouble("X", entity.getX());
        tag.putDouble("Y", entity.getY());
        tag.putDouble("Z", entity.getZ());
        UUID link = JudgmentEncounters.link(entity, entity.getPersistentData());
        if (link != null) tag.putUUID("Encounter", link);
        list.add(tag);
    }

    void followConversion(LivingEntity previous, LivingEntity next) {
        if (next instanceof Player || previous.getUUID().equals(next.getUUID())) return;
        for (String caster : casts.getAllKeys()) {
            if (next instanceof OwnableEntity pet && pet.getOwnerUUID() != null
                    && caster.equals(pet.getOwnerUUID().toString())) continue;
            var locks = casts.getCompound(caster).getList("Locked", Tag.TAG_COMPOUND);
            boolean found = false;
            for (var entry : locks) {
                UUID id = ((CompoundTag)entry).getUUID("UUID");
                if (id.equals(next.getUUID())) { found = false; break; }
                if (id.equals(previous.getUUID())) found = true;
            }
            if (found) {
                appendLock(locks, next);
                setDirty();
            }
        }
    }

    record Locks(Set<UUID> entities, Set<UUID> encounters) {
        boolean matches(Entity candidate) {
            if (entities.contains(candidate.getUUID())) return true;
            if (encounters.isEmpty()) return false;
            var link = JudgmentEncounters.link(candidate, candidate.getPersistentData());
            return link != null && encounters.contains(link);
        }
    }

    Locks land(net.minecraft.server.level.ServerPlayer player) {
        Set<UUID> ids = new java.util.HashSet<>();
        Set<UUID> encounterIds = new java.util.HashSet<>();
        var cast = casts.getCompound(player.getStringUUID());
        long now = player.serverLevel().getServer().overworld().getGameTime();
        var locked = cast.getList("Locked", Tag.TAG_COMPOUND);
        for (int i = 0; i < locked.size(); i++) {
            var t = locked.getCompound(i);
            UUID uuid = t.getUUID("UUID");
            var position = new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"));
            add(new Target(player.getUUID(), uuid, new ResourceLocation(t.getString("Dimension")),
                new ResourceLocation(t.getString("Type")), position, now, true));
            if (t.hasUUID("Encounter")) {
                encounterIds.add(t.getUUID("Encounter"));
                recordController(player.getUUID(), player.serverLevel(), t.getUUID("Encounter"), position);
            }
            ids.add(uuid);
        }
        cast.remove("Locked");
        setDirty();
        return new Locks(ids, encounterIds);
    }

    private static UnleashedVerdicts load(CompoundTag root) {
        var data = new UnleashedVerdicts();
        data.casts = root.getCompound("Casts");
        var list = root.getList("Targets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var tag = list.getCompound(i);
            var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            var type = ResourceLocation.tryParse(tag.getString("Type"));
            if (!tag.hasUUID("Caster") || !tag.hasUUID("UUID") || dimension == null || type == null) continue;
            data.add(new Target(tag.getUUID("Caster"), tag.getUUID("UUID"), dimension, type,
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                tag.getLong("Start"), tag.getBoolean("Boss")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        var list = new ListTag();
        for (var owners : targets.values()) for (Target t : owners) {
            var tag = new CompoundTag();
            tag.putUUID("Caster", t.caster);
            tag.putUUID("UUID", t.uuid);
            tag.putString("Dimension", t.dimension.toString());
            tag.putString("Type", t.type.toString());
            tag.putDouble("X", t.position.x);
            tag.putDouble("Y", t.position.y);
            tag.putDouble("Z", t.position.z);
            tag.putLong("Start", t.start);
            tag.putBoolean("Boss", t.boss);
            list.add(tag);
        }
        root.put("Casts", casts.copy());
        root.put("Targets", list);
        return root;
    }
}
