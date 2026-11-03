package com.buwan.shizuku.sa;

import com.buwan.shizuku.mixin.LivingEntityAccessor;
import com.buwan.shizuku.network.PacketHandler;
import com.buwan.shizuku.network.YmsaForceRemovePacket;
import com.buwan.shizuku.util.LootDropController;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;






import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 3.5.0 版「无铭有名」抹除能力（1.21.1 移植）。
 * 服务器端：ymsa_kill 伤害一击必杀 + discard 彻底移除 + 重生/类型压制；
 * 客户端：YmsaForceRemovePacket 强制移除实体并标记抹除。
 * 深层实体管理器直删与客户端 accessor mixin 已由 1.21.1 公共 API（removeEntity/discard）替代。
 */
public class Ymsa {

    private static final double KILL_RADIUS = 256.0;
    private static final float KILL_DAMAGE = Float.MAX_VALUE;
    private static final int RESPAWN_TRACK_TICKS = 1200;
    private static final int BOSS_SUPPRESS_TICKS = 1200;
    private static final float HIGH_HP_THRESHOLD = 1000.0F;
    private static final List<String> PROTECTION_TAGS = List.of(
            "Immortal", "Protected", "Invincible", "Unkillable", "GodMode", "BossImmune", "Undying", "god");

    private static final Map<UUID, YmsaLogic.CleanupEntry> RESPAWN_TRACKER = new ConcurrentHashMap<>();
    private static final Map<String, Integer> TYPE_SUPPRESSION = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PENDING_DROPS = new ConcurrentHashMap<>();
    private static final Set<UUID> ANNIHILATED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, ServerLevel> ANNIHILATION_PURGE = new ConcurrentHashMap<>();
    private static final Map<UUID, Entity> PENDING_WIPE = new ConcurrentHashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(Ymsa.class);

    private static final List<String> CLASS_BOSS_FRAGMENTS = List.of(
            "wither", "dragon", "boss", "titan", "chaos", "demon", "god", "silence", "execution",
            "zilla", "superbee", "agent", "ancient", "legendary", "mythic", "void", "shadow", "dark",
            "crystal", "elemental", "guardian", "golem", "construct", "abomination", "horror", "nightmare");

    private static final List<String> NAME_BOSS_FRAGMENTS = List.of(
            "凋灵", "龙", "深渊", "终影", "执行", "远古", "传说", "神话", "虚空", "暗影", "黑暗",
            "元素", "守护者", "傀儡", "构造体", "憎恶", "恐怖", "梦魇", "机械", "王", "女王",
            "泰坦", "巫妖", "凤凰", "恶魔", "天使", "领主", "主脑", "皇帝", "霸主", "主宰", "始祖");

    public static InteractionResult doYmsaSlash(LivingEntity entityIn) {
        if (entityIn instanceof ServerPlayer player) {
            protectMainHandBlade(player);
            try {
                performYmsaCast(player);
            } catch (Exception e) {
                LOGGER.error("[SA] Ymsa 施法异常(已隔离, 服务器继续运行): {}", e.toString());
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static void clearAllSAState() {
        RESPAWN_TRACKER.clear();
        TYPE_SUPPRESSION.clear();
        PENDING_DROPS.clear();
        ANNIHILATED.clear();
        ANNIHILATION_PURGE.clear();
        PENDING_WIPE.clear();
    }

    public static boolean isTickSuppressed(LivingEntity entity) {
        return ANNIHILATED.contains(entity.getUUID());
    }

    public static void clearPlayerState(UUID playerUUID) {
        YmsaLogic.filterByKiller(RESPAWN_TRACKER, playerUUID);
    }

    private static void protectMainHandBlade(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && mainHand.isDamageableItem()) {
            mainHand.setDamageValue(mainHand.getDamageValue());
            mainHand.set(net.minecraft.core.component.DataComponents.UNBREAKABLE,
                    net.minecraft.util.Unit.INSTANCE);
            mainHand.set(net.minecraft.core.component.DataComponents.REPAIR_COST, 0);
        }
    }

    private static void performYmsaCast(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        AABB area = player.getBoundingBox().inflate(KILL_RADIUS);
        int killed = 0;
        int forcedDrops = 0;
        int annihilated = 0;

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> shouldKill(e, player))) {
            KillOutcome outcome = killTarget(target, player, level);
            if (outcome.killed) {
                killed++;
            }
            if (outcome.forcedDrops) {
                forcedDrops++;
            }
            if (outcome.annihilated) {
                annihilated++;
            }
        }

        int playersKilled = 0;
        for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, area, e -> e != player)) {
            if (killPlayerTarget(other, player, level)) {
                playersKilled++;
            }
        }

        int projectilesCleared = clearHostileProjectiles(level, area);
        int objectsCleared = sweepBossObjects(player, level);
        LootDropController.conditionalCleanup(player, KILL_RADIUS);
        LOGGER.info("[SA] Ymsa 释放: 击杀实体 {} 个 (强制掉落 {} 个, 绝对抹除 {} 个), 击杀玩家 {} 名, 抹除对象 {} 个, 清除投射物 {} 个",
                killed, forcedDrops, annihilated, playersKilled, objectsCleared, projectilesCleared);
    }

    private static boolean shouldKill(LivingEntity e, ServerPlayer caster) {
        if (e instanceof Player) {
            return false;
        }
        // ⚠️ 26.1：TamableAnimal 的 owner 是 EntityReference，未驯服/只是「可驯服」的生物
        // （狼、猫、狐狸…）它恒为 null ⇒ 直接解引用会 NPE。原版 1.20.1 写法
        // （getOwnerUUID().getUUID()）在同样的情形下其实也会炸，只是被外层 catch 吞掉、
        // 表现为「Ymsa 整次施法被跳过、一个怪都不死」。这里补判空。
        boolean isOwnPet = false;
        if (e instanceof TamableAnimal ta) {
            net.minecraft.world.entity.EntityReference<?> owner = ta.getOwnerReference();
            isOwnPet = owner != null && caster.getUUID().equals(owner.getUUID());
        }
        return YmsaLogic.shouldKillPure(caster.getUUID().equals(e.getUUID()), isOwnPet,
                e.isRemoved() && e.getRemovalReason() != null, false);
    }

    private static int clearHostileProjectiles(ServerLevel level, AABB area) {
        int cleared = 0;
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area)) {
            if (!(projectile.getOwner() instanceof Player)) {
                projectile.discard();
                cleared++;
            }
        }
        return cleared;
    }

    private static boolean killPlayerTarget(ServerPlayer target, ServerPlayer attacker, ServerLevel level) {
        if (target == attacker) {
            return false;
        }
        try {
            stripAllTotems(target);
            target.setHealth(0.0F);
            stripProtections(target);
            target.setLastHurtByMob(attacker);
            DamageSource src = ymsaKillSource(level, attacker);
            target.hurt(src, KILL_DAMAGE);

            for (int i = 0; i < 3 && target.isAlive() && !target.isDeadOrDying(); i++) {
                target.getCombatTracker().recordDamage(src, KILL_DAMAGE);
                target.setHealth(0.0F);
                target.die(src);
            }

            if (target.isAlive() && !target.isDeadOrDying()) {
                target.getEntityData().set(LivingEntityAccessor.shizuku$getDataHealthId(), 0.0F);
                target.getCombatTracker().recordDamage(src, KILL_DAMAGE);
                target.die(src);
            }
        } catch (Exception e) {
            LOGGER.warn("[SA] Ymsa 击杀玩家 {} 失败(已隔离): {}", target.getName().getString(), e.toString());
        }

        return !target.isAlive();
    }

    private static void stripAllTotems(ServerPlayer target) {
        for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
            if (target.getInventory().getItem(i).getItem() == Items.TOTEM_OF_UNDYING) {
                target.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static DamageSource ymsaKillSource(ServerLevel level, ServerPlayer attacker) {
        try {
            ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE,
                    Identifier.fromNamespaceAndPath("shizuku", "ymsa_kill"));
            return level.damageSources().source(key, attacker, attacker);
        } catch (Exception e) {
            LOGGER.debug("[SA] 自定义伤害类型 shizuku:ymsa_kill 不可用, 回退 genericKill: {}", e.toString());
            return level.damageSources().genericKill();
        }
    }

    private static KillOutcome killTarget(LivingEntity target, ServerPlayer player, ServerLevel level) {
        KillOutcome outcome = new KillOutcome();
        if (target.isRemoved()) {
            return outcome;
        }
        if (!target.isAlive()) {
            try {
                if (target.getRemovalReason() == null) {
                    target.setRemoved(Entity.RemovalReason.KILLED);
                }
                if (level.getEntity(target.getId()) == target) {
                    outcome.annihilated = annihilate(target, player, level);
                }
                outcome.killed = true;
            } catch (Exception e) {
                LOGGER.warn("[SA] Ymsa 清理死亡残留 {} 失败(已隔离): {}", target.getName().getString(), e.toString());
                try {
                    if (level.getEntity(target.getId()) == target) {
                        outcome.annihilated = annihilate(target, player, level);
                    }
                } catch (Exception ignored) {
                }
            }
            return outcome;
        }

        DamageSource src = ymsaKillSource(level, player);
        UUID targetId = target.getUUID();
        PENDING_DROPS.put(targetId, Boolean.TRUE);

        try {
            stripProtections(target);
            target.setLastHurtByMob(player);
            target.hurt(src, KILL_DAMAGE);
            if (!target.isDeadOrDying()) {
                target.getCombatTracker().recordDamage(src, KILL_DAMAGE);
                target.setHealth(0.0F);
                target.die(src);
            }

            if (!target.isDeadOrDying()) {
                target.getEntityData().set(LivingEntityAccessor.shizuku$getDataHealthId(), 1.0F);
                if (!target.isRemoved() && !target.isDeadOrDying()) {
                    target.getEntityData().set(LivingEntityAccessor.shizuku$getDataHealthId(), 0.0F);
                    target.getCombatTracker().recordDamage(src, KILL_DAMAGE);
                    target.die(src);
                }
            }

            if (PENDING_DROPS.remove(targetId) != null && !target.isRemoved()) {
                // die() 已触发完整死亡掉落；若仍有残留则再尝试一次。
                target.die(src);
                outcome.forcedDrops = true;
            }

            if (!target.isRemoved()) {
                target.discard();
            }
            if (target.getRemovalReason() == null) {
                target.setRemoved(Entity.RemovalReason.KILLED);
            }

            outcome.killed = true;
            if (isBossLike(target)) {
                broadcastClientErase(target, level, false);
            }

            if (level.getEntity(target.getId()) == target) {
                outcome.annihilated = annihilate(target, player, level);
            }
        } catch (Exception e) {
            LOGGER.warn("[SA] Ymsa 击杀 {} 失败(已隔离, 不影响其他目标): {}", target.getName().getString(), e.toString());
            try {
                if (!target.isRemoved()) {
                    target.discard();
                }
            } catch (Exception ignored) {
            }
            try {
                if (level.getEntity(target.getId()) == target) {
                    outcome.annihilated = annihilate(target, player, level);
                }
            } catch (Exception ignored) {
            }
        } finally {
            PENDING_DROPS.remove(targetId);
        }

        if (!target.isRemoved()) {
            suppressRespawn(target, player);
        }

        return outcome;
    }

    private static void stripProtections(LivingEntity target) {
        target.removeAllEffects();
        target.setInvulnerable(false);
        // Fabric 无 getPersistentData()：第三方保护标签（Immortal/Protected/Invincible）无法在此剥离。
    }

    private static void suppressRespawn(LivingEntity target, ServerPlayer player) {
        RESPAWN_TRACKER.put(target.getUUID(), new YmsaLogic.CleanupEntry(player.getUUID(), RESPAWN_TRACK_TICKS));
        if (isBossLike(target)) {
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
            if (key != null) {
                TYPE_SUPPRESSION.put(key.toString(), BOSS_SUPPRESS_TICKS);
            }
        }
    }

    private static boolean isBossLike(LivingEntity entity) {
        boolean byKeywords = YmsaLogic.matchesBossKeywordsPure(
                entity.getClass().getName().toLowerCase(),
                entity.getName().getString().toLowerCase(),
                CLASS_BOSS_FRAGMENTS, NAME_BOSS_FRAGMENTS);
        return byKeywords || entity.getMaxHealth() > HIGH_HP_THRESHOLD || !isMinecraftNamespace(entity);
    }

    private static boolean isMinecraftNamespace(LivingEntity entity) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null && "minecraft".equals(key.getNamespace());
    }

    private static boolean annihilate(LivingEntity target, ServerPlayer player, ServerLevel level) {
        UUID targetId = target.getUUID();
        ANNIHILATED.add(targetId);

        try {
            DamageSource src = ymsaKillSource(level, player);
            wipeBossHealth(target);

            try {
                target.getEntityData().set(LivingEntityAccessor.shizuku$getDataHealthId(), 1.0F);
            } catch (Exception ignored) {
            }

            for (int i = 0; i < 30 && target.isAlive() && !target.isRemoved(); i++) {
                clearHitCooldowns(target);
                target.hurt(src, KILL_DAMAGE);
            }

            if (PENDING_DROPS.remove(targetId) != null && !target.isRemoved()) {
                target.die(src);
            }

            target.kill((net.minecraft.server.level.ServerLevel) target.level());
            if (level.getEntity(target.getId()) == target) {
                physicallyRemove(target, level);
            }

            ANNIHILATION_PURGE.put(targetId, level);
            LOGGER.warn("[SA] Ymsa 绝对抹除目标(持续净化): {}", target.getName().getString());
            return true;
        } catch (Exception e) {
            LOGGER.warn("[SA] Ymsa 绝对抹除 {} 失败(已隔离): {}", target.getName().getString(), e.toString());
            try {
                if (level.getEntity(target.getId()) == target) {
                    physicallyRemove(target, level);
                }
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    private static void wipeBossHealth(LivingEntity target) {
        try {
            float rawHealth = target.getEntityData().get(LivingEntityAccessor.shizuku$getDataHealthId());
            try {
                target.setHealth(0.0F);
            } catch (Exception ignored) {
            }
            try {
                target.getEntityData().set(LivingEntityAccessor.shizuku$getDataHealthId(), 0.0F);
            } catch (Exception ignored) {
            }

            // 反射：把与当前血量相等的 float 字段全部置 0（针对自带血量缓存的 Boss）
            for (Class<?> clazz = target.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                for (Field field : clazz.getDeclaredFields()) {
                    try {
                        if (field.getType() == float.class) {
                            field.setAccessible(true);
                            float fv = field.getFloat(target);
                            if (Math.abs(fv - rawHealth) < 0.01F) {
                                field.setFloat(target, 0.0F);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            for (Class<?> clazz = target.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                try {
                    Field dead = clazz.getDeclaredField("isDead");
                    dead.setAccessible(true);
                    if (dead.getType() == boolean.class) {
                        dead.setBoolean(target, true);
                    }
                    break;
                } catch (NoSuchFieldException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void clearHitCooldowns(LivingEntity target) {
        target.invulnerableTime = 0;
        try {
            for (Class<?> clazz = target.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                try {
                    Field field = clazz.getDeclaredField("iFrames");
                    field.setAccessible(true);
                    field.setInt(target, 0);
                    break;
                } catch (NoSuchFieldException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void physicallyRemove(LivingEntity target, ServerLevel level) {
        // 1.21.1 公共 API 版“物理移除”：discard + 区块追踪器移除 + 客户端广播抹除。
        target.setRemoved(Entity.RemovalReason.KILLED);
        try {
            target.setLevelCallback(EntityInLevelCallback.NULL);
        } catch (Exception ignored) {
        }
        try {
            level.getChunkSource().removeEntity(target);
        } catch (Exception ignored) {
        }

        broadcastClientErase(target, level, true);

        PENDING_WIPE.put(target.getUUID(), target);
    }

    private static void broadcastClientErase(LivingEntity target, ServerLevel level, boolean typeKill) {
        try {
            int[] entityIds = new int[]{target.getId()};

            boolean[] typeKills = new boolean[entityIds.length];
            Arrays.fill(typeKills, typeKill);

            // Fabric 无 PartEntity：按单体实体处理（多部件 Boss 由服务端移除兜底）。
            UUID[] uuids = new UUID[]{target.getUUID()};

            PacketHandler.sendToAll(new YmsaForceRemovePacket(entityIds, uuids, typeKills));
        } catch (Exception ignored) {
        }
    }

    private static void wipeEntityFields(Entity entity) {
        try {
            for (Class<?> clazz = entity.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                for (Field field : clazz.getDeclaredFields()) {
                    try {
                        int mod = field.getModifiers();
                        if (!Modifier.isStatic(mod) && !Modifier.isFinal(mod)) {
                            field.setAccessible(true);
                            Class<?> type = field.getType();
                            if (type == boolean.class) {
                                field.setBoolean(entity, false);
                            } else if (type == byte.class) {
                                field.setByte(entity, (byte) 0);
                            } else if (type == short.class) {
                                field.setShort(entity, (short) 0);
                            } else if (type == int.class) {
                                field.setInt(entity, 0);
                            } else if (type == long.class) {
                                field.setLong(entity, 0L);
                            } else if (type == float.class) {
                                field.setFloat(entity, 0.0F);
                            } else if (type == double.class) {
                                field.setDouble(entity, 0.0);
                            } else if (type == Boolean.class) {
                                field.set(entity, Boolean.FALSE);
                            } else if (type == Byte.class) {
                                field.set(entity, (byte) 0);
                            } else if (type == Short.class) {
                                field.set(entity, (short) 0);
                            } else if (type == Integer.class) {
                                field.set(entity, 0);
                            } else if (type == Long.class) {
                                field.set(entity, 0L);
                            } else if (type == Float.class) {
                                field.set(entity, 0.0F);
                            } else if (type == Double.class) {
                                field.set(entity, 0.0);
                            } else if (type == String.class) {
                                field.set(entity, "");
                            } else if (Collection.class.isAssignableFrom(type)) {
                                if (field.get(entity) instanceof Collection<?> collection) {
                                    try {
                                        collection.clear();
                                    } catch (Exception ignored) {
                                    }
                                }
                            } else if (type == Entity.RemovalReason.class) {
                                field.set(entity, Entity.RemovalReason.DISCARDED);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void processPendingPhysicalRemovals(MinecraftServer server) {
        if (!ANNIHILATION_PURGE.isEmpty()) {
            for (Map.Entry<UUID, ServerLevel> entry : ANNIHILATION_PURGE.entrySet()) {
                ServerLevel level = entry.getValue();
                if (level != null && level == server.getLevel(level.dimension())) {
                    Entity entity = level.getEntity(entry.getKey());
                    if (entity instanceof LivingEntity living) {
                        try {
                            physicallyRemove(living, level);
                        } catch (Exception e) {
                            LOGGER.warn("[SA] Ymsa 持续净化 {} 失败(已隔离): {}", living.getName().getString(), e.toString());
                        }
                    }
                }
            }
        }
    }

    private static int sweepBossObjects(ServerPlayer player, ServerLevel level) {
        int cleared = 0;
        try {
            Class<?> levelClass = Class.forName("flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurLevel");
            Field listField = levelClass.getDeclaredField("metapotentFlashfurList");
            listField.setAccessible(true);
            if (listField.get(null) instanceof List<?> list && !list.isEmpty()) {
                List<Object> victims = new ArrayList<>();
                for (Object obj : list) {
                    if (obj != null) {
                        try {
                            net.minecraft.world.phys.Vec3 pos = (net.minecraft.world.phys.Vec3) obj.getClass().getField("pos").get(obj);
                            if (player.position().distanceToSqr(pos) <= KILL_RADIUS * KILL_RADIUS) {
                                victims.add(obj);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

                if (victims.isEmpty()) {
                    return 0;
                }

                for (Object victim : victims) {
                    try {
                        Object bossEvent = victim.getClass().getField("bossEvent").get(victim);
                        if (bossEvent != null) {
                            bossEvent.getClass().getMethod("setVisible", boolean.class).invoke(bossEvent, false);
                            try {
                                bossEvent.getClass().getMethod("removeAllPlayers").invoke(bossEvent);
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

                if (removeObjectsFromProtectedList(list, victims)) {
                    deleteFlashfurSaveFiles(level.getServer());
                    cleared = victims.size();
                }
                return cleared;
            }
            return 0;
        } catch (ClassNotFoundException e) {
            return 0;
        } catch (Exception e) {
            LOGGER.warn("[SA] Ymsa 清扫炫彩猫失败(已隔离): {}", e.toString());
            return cleared;
        }
    }

    @SuppressWarnings("removal")
    private static boolean removeObjectsFromProtectedList(List<?> list, List<Object> victims) {
        try {
            ArrayList<?> arrayList = (ArrayList<?>) list;
            Field dataField = ArrayList.class.getDeclaredField("elementData");
            Field sizeField = ArrayList.class.getDeclaredField("size");
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            long dataOffset = unsafe.objectFieldOffset(dataField);
            long sizeOffset = unsafe.objectFieldOffset(sizeField);
            Object[] data = (Object[]) unsafe.getObject(arrayList, dataOffset);
            int size = unsafe.getInt(arrayList, sizeOffset);
            Set<Object> victimSet = new HashSet<>(victims);
            List<Object> survivors = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                Object obj = data[i];
                if (obj != null && !victimSet.contains(obj)) {
                    survivors.add(obj);
                }
            }
            unsafe.putObject(arrayList, dataOffset, survivors.toArray());
            unsafe.putInt(arrayList, sizeOffset, survivors.size());
            return true;
        } catch (Exception e) {
            LOGGER.warn("[SA] Ymsa Unsafe 重建炫彩猫列表失败: {}", e.toString());
            return false;
        }
    }

    private static void deleteFlashfurSaveFiles(MinecraftServer server) {
        try {
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("metapotent_flashfurs");
            if (Files.isDirectory(dir)) {
                try (Stream<Path> stream = Files.list(dir)) {
                    stream.filter(p -> p.getFileName().toString().endsWith(".omnimob")).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void handleServerTickEnd(MinecraftServer server) {
        YmsaLogic.tickCleanups(RESPAWN_TRACKER);
        YmsaLogic.tickTypeSuppression(TYPE_SUPPRESSION);
        processPendingPhysicalRemovals(server);
        processPendingWipes();
    }

    private static void processPendingWipes() {
        if (!PENDING_WIPE.isEmpty()) {
            for (Map.Entry<UUID, Entity> entry : PENDING_WIPE.entrySet()) {
                try {
                    wipeEntityFields(entry.getValue());
                } catch (Exception e) {
                    LOGGER.warn("[SA] Ymsa 字段清空失败(已隔离): {}", e.toString());
                }
            }
            PENDING_WIPE.clear();
        }
    }

    public static void handleDropsProcessed(LivingEntity entity) {
        if (entity != null) {
            PENDING_DROPS.remove(entity.getUUID());
        }
    }

    public static boolean shouldCancelJoin(Entity entity) {
        if (entity instanceof LivingEntity living) {
            if (RESPAWN_TRACKER.containsKey(living.getUUID())) {
                return true;
            }
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
            return key != null && TYPE_SUPPRESSION.containsKey(key.toString());
        }
        return false;
    }

    
    public static void onServerTick(MinecraftServer server) {
        handleServerTickEnd(server);
    }

    public static void onEntityJoin(Entity entity) {
        if (shouldCancelJoin(entity)) {
            entity.discard();
        }
    }

    public static void onPlayerChangedDimension(ServerPlayer player) {
        clearPlayerState(player.getUUID());
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        clearPlayerState(player.getUUID());
    }

    private static final class KillOutcome {
        boolean killed;
        boolean forcedDrops;
        boolean annihilated;
    }
}
