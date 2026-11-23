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
import net.minecraft.resources.Identifier;
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
import com.buwan.shizuku.compat.PartEntity;
import net.minecraft.core.registries.BuiltInRegistries;

/** Server-thread only. Overworld game time is shared by all encounter dimensions. */
final class UnleashedVerdicts extends SavedData {
    private record Target(UUID caster, UUID uuid, Identifier dimension, Identifier type,
                          Vec3 position, long start, boolean boss) {}
    private final Map<UUID, List<Target>> targets = new HashMap<>();
    private final Map<Identifier, List<Target>> encounters = new HashMap<>();

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
            if (t.start == now && t.dimension.equals(level.dimension().identifier()) && area.contains(t.position)) {
                add(new Target(caster, t.uuid, t.dimension, t.type, t.position, now, t.boss));
                setDirty();
            }
        }
    }

    /**
     * 26.1 的存档数据改成 codec 驱动：{@code SavedData.Factory} 与 {@code save/load(CompoundTag, Provider)}
     * 都已删除，改为 {@code SavedDataType(id, 构造器, Codec, DataFixTypes)} + {@code SavedDataStorage#computeIfAbsent}。
     *
     * <p>这里用 {@code CompoundTag.CODEC.xmap(...)} 把「原来的整段 NBT 读写」原样包成 codec，
     * 既不用重写结构、存档格式也与 1.21.1 完全一致（tag 仍存在 "data" 下面）。</p>
     */
    private static final net.minecraft.world.level.saveddata.SavedDataType<UnleashedVerdicts> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    Identifier.fromNamespaceAndPath("shizuku", "unleashed_verdicts"),
                    UnleashedVerdicts::new,
                    CompoundTag.CODEC.xmap(UnleashedVerdicts::load, UnleashedVerdicts::save),
                    // 自定义 SavedData 没有专属的 DataFixTypes，跟社区惯例一样挂 LEVEL（版本一致时不会跑 fixer）。
                    net.minecraft.util.datafix.DataFixTypes.LEVEL);

    static UnleashedVerdicts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    void record(UUID caster, Entity entity, boolean boss) {
        var level = (ServerLevel)entity.level();
        var type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (type == null) return;
        add(new Target(caster, entity.getUUID(), level.dimension().identifier(), type,
            entity.position(), level.getServer().overworld().getGameTime(), boss));
        UUID encounter = JudgmentEncounters.link(entity, com.buwan.shizuku.util.PersistentData.of(entity));
        if (encounter != null) recordController(caster, level, encounter, entity.position());
        if (boss && com.buwan.shizuku.util.NbtUuids.has(com.buwan.shizuku.util.PersistentData.of(entity), "shizuku:spawn_source"))
            recordController(caster, level, com.buwan.shizuku.util.NbtUuids.get(com.buwan.shizuku.util.PersistentData.of(entity), "shizuku:spawn_source"), entity.position());
        setDirty();
    }

    void recordController(UUID caster, ServerLevel level, UUID id, Vec3 location) {
        long now = level.getServer().overworld().getGameTime();
        if (targets.getOrDefault(id, List.of()).stream().noneMatch(t -> t.caster.equals(caster) && t.start == now)) {
            add(new Target(caster, id, level.dimension().identifier(), Identifier.fromNamespaceAndPath("shizuku", "controller"), location, now, false));
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

    boolean blocksSpawn(ServerLevel level, Identifier type, Vec3 position) {
        long now = level.getServer().overworld().getGameTime();
        return encounters.getOrDefault(level.dimension().identifier(), List.of()).stream().anyMatch(t ->
            t.type.equals(type) && now - t.start < JudgmentConfig.RETENTION.get() && t.position.distanceToSqr(position) <= JudgmentConfig.RADIUS.get() * JudgmentConfig.RADIUS.get());
    }

    boolean matches(ServerLevel level, Entity entity) {
        if (targets.isEmpty() || entity instanceof Player) return false;
        if (entity instanceof ItemEntity drop) return JudgmentEncounters.blockedItem(level, drop.getItem());
        UUID owner = null;
        if (entity instanceof PartEntity part) owner = part.getParent().getUUID();
        else if (entity instanceof com.buwan.shizuku.mixin.JudgmentProjectileAccessor projectile) owner = projectile.shizuku$ownerRef() == null ? null : projectile.shizuku$ownerRef().getUUID();
        // 26.1：OwnableEntity#getOwnerUUID() 已删 → getOwnerReference().getUUID()（引用可能为空）。
        else if (entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null)
            owner = ownable.getOwnerReference().getUUID();
        else if (entity instanceof TraceableEntity traceable) {
            Entity parent = traceable.getOwner();
            if (parent != null) owner = parent.getUUID();
        }
        long now = level.getServer().overworld().getGameTime();
        if (active(entity.getUUID(), now) || (owner != null && active(owner, now))) return true;
        var tag = com.buwan.shizuku.util.PersistentData.of(entity);
        UUID encounter = JudgmentEncounters.link(entity, tag);
        if (encounter != null && active(encounter, now)) return true;
        if (com.buwan.shizuku.util.NbtUuids.has(tag, "shizuku:spawn_source") && active(com.buwan.shizuku.util.NbtUuids.get(tag, "shizuku:spawn_source"), now)) return true;
        if (!(entity instanceof LivingEntity living)) return false;
        var type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        for (Target t : encounters.getOrDefault(level.dimension().identifier(), List.of())) {
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
        for (Entity part : com.buwan.shizuku.compat.Multiparts.of(level)) if (matches(level, part)) return true;
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
        com.buwan.shizuku.util.NbtUuids.put(cast, "Blade", blade);
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
        var cast = casts.getCompoundOrEmpty(caster.toString());
        return com.buwan.shizuku.util.NbtUuids.has(cast, "Blade") && com.buwan.shizuku.util.NbtUuids.get(cast, "Blade").equals(blade) ? cast.getIntOr("Age", -1) : -1;
    }

    boolean advance(UUID caster, int age) {
        var cast = casts.getCompoundOrEmpty(caster.toString());
        if (!com.buwan.shizuku.util.NbtUuids.has(cast, "Blade") || age <= cast.getIntOr("Age", -1)) return false;
        cast.putInt("Age", age);
        setDirty();
        return true;
    }

    void lockTargets(net.minecraft.server.level.ServerPlayer player) {
        var cast = casts.getCompoundOrEmpty(player.getStringUUID());
        var list = new ListTag();
        var area = player.getBoundingBox().inflate(320);
        for (Entity entity : new JudgmentIndex(((net.minecraft.server.level.ServerLevel) player.level())).verdictTargets(area, player.getUUID())) {
            if (!(entity instanceof LivingEntity living ? Ymsa.isUnleashedBoss(living) : JudgmentEncounters.identifies(entity))) continue;
            appendLock(list, entity);
        }
        cast.put("Locked", list);
        setDirty();
    }

    private static void appendLock(ListTag list, Entity entity) {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key == null) return;
        var tag = new CompoundTag();
        com.buwan.shizuku.util.NbtUuids.put(tag, "UUID", entity.getUUID());
        tag.putString("Type", key.toString());
        tag.putString("Dimension", entity.level().dimension().identifier().toString());
        tag.putDouble("X", entity.getX());
        tag.putDouble("Y", entity.getY());
        tag.putDouble("Z", entity.getZ());
        UUID link = JudgmentEncounters.link(entity, com.buwan.shizuku.util.PersistentData.of(entity));
        if (link != null) com.buwan.shizuku.util.NbtUuids.put(tag, "Encounter", link);
        list.add(tag);
    }

    void followConversion(LivingEntity previous, LivingEntity next) {
        if (next instanceof Player || previous.getUUID().equals(next.getUUID())) return;
        for (String caster : casts.keySet()) {
            if (next instanceof OwnableEntity pet && pet.getOwnerReference() != null
                    && caster.equals(pet.getOwnerReference().getUUID().toString())) continue;
            var locks = casts.getCompoundOrEmpty(caster).getListOrEmpty("Locked");
            boolean found = false;
            for (var entry : locks) {
                UUID id = com.buwan.shizuku.util.NbtUuids.get(((CompoundTag)entry), "UUID");
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
            var link = JudgmentEncounters.link(candidate, com.buwan.shizuku.util.PersistentData.of(candidate));
            return link != null && encounters.contains(link);
        }
    }

    Locks land(net.minecraft.server.level.ServerPlayer player) {
        Set<UUID> ids = new java.util.HashSet<>();
        Set<UUID> encounterIds = new java.util.HashSet<>();
        var cast = casts.getCompoundOrEmpty(player.getStringUUID());
        long now = ((net.minecraft.server.level.ServerLevel) player.level()).getServer().overworld().getGameTime();
        var locked = cast.getListOrEmpty("Locked");
        for (int i = 0; i < locked.size(); i++) {
            var t = locked.getCompoundOrEmpty(i);
            UUID uuid = com.buwan.shizuku.util.NbtUuids.get(t, "UUID");
            var position = new Vec3(t.getDoubleOr("X", 0.0D), t.getDoubleOr("Y", 0.0D), t.getDoubleOr("Z", 0.0D));
            // 26.1：CompoundTag#getString 返回 Optional，改用 getStringOr；
            // 写端（appendLock）一定写了这两项，坏数据直接跳过而不是抛 IllegalArgumentException。
            var lockedDimension = Identifier.tryParse(t.getStringOr("Dimension", ""));
            var lockedType = Identifier.tryParse(t.getStringOr("Type", ""));
            if (lockedDimension == null || lockedType == null) continue;
            add(new Target(player.getUUID(), uuid, lockedDimension, lockedType, position, now, true));
            if (com.buwan.shizuku.util.NbtUuids.has(t, "Encounter")) {
                encounterIds.add(com.buwan.shizuku.util.NbtUuids.get(t, "Encounter"));
                recordController(player.getUUID(), ((net.minecraft.server.level.ServerLevel) player.level()), com.buwan.shizuku.util.NbtUuids.get(t, "Encounter"), position);
            }
            ids.add(uuid);
        }
        cast.remove("Locked");
        setDirty();
        return new Locks(ids, encounterIds);
    }

    private static UnleashedVerdicts load(CompoundTag root) {
        var data = new UnleashedVerdicts();
        data.casts = root.getCompoundOrEmpty("Casts");
        var list = root.getListOrEmpty("Targets");
        for (int i = 0; i < list.size(); i++) {
            var tag = list.getCompoundOrEmpty(i);
            var dimension = Identifier.tryParse(tag.getStringOr("Dimension", ""));
            var type = Identifier.tryParse(tag.getStringOr("Type", ""));
            if (!com.buwan.shizuku.util.NbtUuids.has(tag, "Caster") || !com.buwan.shizuku.util.NbtUuids.has(tag, "UUID") || dimension == null || type == null) continue;
            data.add(new Target(com.buwan.shizuku.util.NbtUuids.get(tag, "Caster"), com.buwan.shizuku.util.NbtUuids.get(tag, "UUID"), dimension, type,
                new Vec3(tag.getDoubleOr("X", 0.0D), tag.getDoubleOr("Y", 0.0D), tag.getDoubleOr("Z", 0.0D)),
                tag.getLongOr("Start", 0L), tag.getBooleanOr("Boss", false)));
        }
        return data;
    }

    private CompoundTag save() {
        var root = new CompoundTag();
        var list = new ListTag();
        for (var owners : targets.values()) for (Target t : owners) {
            var tag = new CompoundTag();
            com.buwan.shizuku.util.NbtUuids.put(tag, "Caster", t.caster);
            com.buwan.shizuku.util.NbtUuids.put(tag, "UUID", t.uuid);
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
