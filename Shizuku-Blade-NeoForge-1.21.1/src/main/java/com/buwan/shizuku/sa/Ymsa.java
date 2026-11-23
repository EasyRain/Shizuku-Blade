package com.buwan.shizuku.sa;

import com.buwan.shizuku.mixin.LivingEntityAccessor;
import com.buwan.shizuku.network.PacketHandler;
import com.buwan.shizuku.network.YmsaForceRemovePacket;
import com.buwan.shizuku.util.LootDropController;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
    // 3.8.0 起审判动画的时序（与 Forge 版一致）
    public static final int STRIKE_TICK = 8;
    public static final int RIFT_CLOSED_TICK = 38;
    public static final int VERDICT_TICK = 42;
    public static final int DOMAIN_RADIUS = 128;
    public static final int SHEATH_TICK = 54;
    public static final int END_TICK = 60;
    public static final int RECOVERY_FRAME = END_TICK * 3 / 2;
    public static final net.minecraft.resources.ResourceLocation MOTION =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("shizuku", "combostate/judgment.vmd");
    static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> BOSS_TYPES =
        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("shizuku", "judgment_bosses"));


    private static final double KILL_RADIUS = 256.0;
    private static final double OVERDRIVE_RADIUS = 512.0;
    private static final double UNLEASHED_RADIUS = 320.0;
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

    /** 调试开关：-Dshizuku.judgment.debug=true 才输出逐目标诊断。 */
    private static final boolean JUDGMENT_DEBUG = Boolean.getBoolean("shizuku.judgment.debug");

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
            // 解放状态下走 overdrive：由 JudgmentExecution 接管无敌/假死等特殊血量逻辑
            boolean overdrive = UnleashedJudgment.isCasting(player) && JudgmentExecution.supported();
            if (overdrive) {
                JudgmentExecution.arm();
            }
            try {
                performYmsaCast(player);
            } catch (Exception e) {
                LOGGER.error("[SA] Ymsa 施法异常(已隔离, 服务器继续运行): {}", e.toString());
            } finally {
                if (overdrive) {
                    JudgmentExecution.disarm();
                }
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
                    new net.minecraft.world.item.component.Unbreakable(false));
            mainHand.set(net.minecraft.core.component.DataComponents.REPAIR_COST, 0);
        }
    }

    private static void performYmsaCast(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        // 对齐上游：普通 256 / 解放 320 / 过载（审判执行层已 arm）512
        boolean unleashed = UnleashedJudgment.isCasting(player);
        double radius = unleashed
                ? (JudgmentExecution.armed() ? OVERDRIVE_RADIUS : UNLEASHED_RADIUS)
                : KILL_RADIUS;
        AABB area = player.getBoundingBox().inflate(radius);
        int killed = 0;
        int forcedDrops = 0;
        int annihilated = 0;

        // 释放时按着 Shift = 抹除模式：实体直接抹掉、地上的掉落与经验也一并清掉（保持原版手感）。
        // 不看是否解放 —— 用户确认这样手感一致；不按 Shift 则走正常死亡流程。
        boolean cleanDrops = CLEANUP_LATCH.getOrDefault(player.getUUID(), player.isShiftKeyDown());
        boolean wipe = cleanDrops && player.isShiftKeyDown();

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> shouldKill(e, player))) {
            if (!wipe && BossCompat.trySpecial(target, player)) {
                killed++;
                continue;
            }
            KillOutcome outcome = killTarget(target, player, level, wipe);
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

        int playersKilled = damageNearbyPlayers(player, area);

        int projectilesCleared = clearHostileProjectiles(level, area);
        int objectsCleared = sweepBossObjects(player, level);
        CLEANUP_LATCH.remove(player.getUUID());
        if (cleanDrops) {
            LootDropController.cleanupDrops(player, KILL_RADIUS);
        }
        LOGGER.info("[SA] Ymsa 释放: 击杀实体 {} 个 (强制掉落 {} 个, 绝对抹除 {} 个), 击杀玩家 {} 名, 抹除对象 {} 个, 清除投射物 {} 个",
                killed, forcedDrops, annihilated, playersKilled, objectsCleared, projectilesCleared);
    }

    private static boolean shouldKill(LivingEntity e, ServerPlayer caster) {
        if (e instanceof Player) {
            return false;
        }
        // 豁免：自己 + 自己的宠物。
        // 用 OwnableEntity 而不是只判 TamableAnimal —— 上游 JudgmentIndex 也是这么写的，
        // 这样能一并覆盖马/Allay/以及实现 OwnableEntity 的模组生物（如车万女仆的 EntityMaid）。
        boolean isOwnPet = e instanceof net.minecraft.world.entity.OwnableEntity owned
                && owned.getOwnerUUID() != null
                && caster.getUUID().equals(owned.getOwnerUUID());
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

    /**
     * 对范围内允许交战的玩家造成伤害（对应上游 3.8.0 的同名方法）。
     *
     * <p>玩家**不进入**审判处决流程，这里的 PvP 伤害只在「伤害玩家」开关打开时、
     * 经由审判专属伤害源单独结算。</p>
     */
    static int damageNearbyPlayers(ServerPlayer caster, AABB area) {
        if (!UnleashedJudgment.canDamagePlayers(caster)) return 0;
        ServerLevel level = caster.serverLevel();
        DamageSource source = ymsaKillSource(level, caster);
        if (source.getEntity() == null) source = level.damageSources().playerAttack(caster);
        int killed = 0;
        for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, area,
                player -> player != caster && player.isAlive())) {
            if (!caster.canHarmPlayer(target) || UnleashedJudgment.isCasting(target)) continue;
            if (target.hurt(source, KILL_DAMAGE) && target.isDeadOrDying()) killed++;
        }
        return killed;
    }

    /**
     * 把实体推进「将死」状态：停睡、记录伤害、血量写 0、发 ENTITY_DAMAGE 事件。
     *
     * <p>照 ae2lt 的 {@code OverloadExecutionService#prepareLethalState}：目的是让随后的
     * {@code die()} 走完整的原版死亡流程（模组在 die 里挂钩子、发成就、掉战利品）。</p>
     */
/** 直接抹掉：remove → discard → setRemoved，三步都试（有的模组会重写其中某个）。 */
    private static void forceErase(LivingEntity target, ServerLevel level) {
        try {
            if (!target.isRemoved()) target.remove(Entity.RemovalReason.KILLED);
            if (!target.isRemoved()) target.discard();
            if (target.getRemovalReason() == null) target.setRemoved(Entity.RemovalReason.KILLED);
        } catch (Throwable t) {
            LOGGER.debug("[SA] forceErase 异常: {}", t.toString());
        }
    }

/** 把实体推进「将死」状态（照 ae2lt 的 OverloadExecutionService#prepareLethalState）。 */
    /** 处理抹除重试队列（模组实体可能把 remove()/setRemoved() 重写成 no-op）。 */
    private static void processForceWipe(MinecraftServer server) {
        if (FORCE_WIPE.isEmpty()) return;
        for (var entry : java.util.List.copyOf(FORCE_WIPE.entrySet())) {
            UUID id = entry.getKey();
            int left = entry.getValue();
            Entity found = null;
            ServerLevel foundLevel = null;
            for (ServerLevel l : server.getAllLevels()) {
                Entity e = l.getEntity(id);
                if (e != null) { found = e; foundLevel = l; break; }
            }
            if (!(found instanceof LivingEntity living) || living.isRemoved()) {
                FORCE_WIPE.remove(id);
                FORCE_WIPE_CASTER.remove(id);
                continue;
            }
            ServerPlayer caster = FORCE_WIPE_CASTER.get(id) == null ? null
                    : server.getPlayerList().getPlayer(FORCE_WIPE_CASTER.get(id));
            if (left <= 0) {
                FORCE_WIPE.remove(id);
                {
                    // 本端口没有索引摘除能力：两种模式统一用 annihilate 收尾
                    forceErase(living, foundLevel);
                    if (foundLevel.getEntity(id) == living) {
                        annihilate(living, caster, foundLevel);
                        LOGGER.warn("[SA] 抹除兜底（annihilate）: {}", living.getClass().getSimpleName());
                    }
                }
                if (foundLevel.getEntity(id) == living && !living.isRemoved()) {
                    try {
                        living.discard();
                        living.setRemoved(Entity.RemovalReason.KILLED);
                    } catch (Throwable ignored) {
                    }
                }
                FORCE_WIPE_CASTER.remove(id);
                continue;
            }
            forceErase(living, foundLevel);
            if (living.isRemoved() || foundLevel.getEntity(id) != living) {
                FORCE_WIPE.remove(id);
            } else {
                FORCE_WIPE.put(id, left - 1);
            }
        }
    }

/** 处理补刀队列（模组 boss 常有 2~20 tick 的受击冷却，一次 hurt 打不死）。 */
    private static void processExtraHits(MinecraftServer server) {
        if (EXTRA_HITS.isEmpty()) return;
        for (var entry : java.util.List.copyOf(EXTRA_HITS.entrySet())) {
            UUID id = entry.getKey();
            int left = entry.getValue();
            DamageSource src = EXTRA_HITS_SRC.get(id);
            if (left <= 0 || src == null) {
                EXTRA_HITS.remove(id);
                EXTRA_HITS_SRC.remove(id);
                // 抹除兜底：补刀 5 tick 仍未死亡（模组完全不走原版死亡）→ 强制移除，
                // 否则这类 boss 会变成「永远杀不掉」。这与上游「无条件 remove(KILLED)」的最终手段一致，
                // 区别只是我们把它放在「正常死亡 + 补刀」都失败之后。
                for (ServerLevel l : server.getAllLevels()) {
                    Entity e = l.getEntity(id);
                    if (e instanceof LivingEntity living2 && !living2.isRemoved()) {
                        LOGGER.info("[SA] 抹除兜底: {}", living2.getClass().getSimpleName());
                        try {
                            living2.remove(Entity.RemovalReason.KILLED);
                            if (living2.getRemovalReason() == null) {
                                living2.setRemoved(Entity.RemovalReason.KILLED);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
                continue;
            }
            Entity found = null;
            for (ServerLevel l : server.getAllLevels()) {
                Entity e = l.getEntity(id);
                if (e != null) { found = e; break; }
            }
            if (!(found instanceof LivingEntity living) || living.isRemoved()
                    || ((LivingEntityAccessor) living).shizuku$isDead()) {
                EXTRA_HITS.remove(id);
                EXTRA_HITS_SRC.remove(id);
                continue;
            }
            try {
                JudgmentExecution.breakInvulnerability(living);
                if (src.getEntity() instanceof LivingEntity killerMob) {
                    living.setLastHurtByMob(killerMob);
                }
                living.hurt(src, Float.MAX_VALUE);
                if (!((LivingEntityAccessor) living).shizuku$isDead()) {
                    living.setHealth(0.0F);
                    living.die(src);
                }
            } catch (Throwable t) {
                LOGGER.debug("[SA] 补刀失败 {}", t.toString());
            }
            if (((LivingEntityAccessor) living).shizuku$isDead() || living.isRemoved()) {
                EXTRA_HITS.remove(id);
                EXTRA_HITS_SRC.remove(id);
                continue;
            }
            if (left - 1 > 0) {
                EXTRA_HITS.put(id, left - 1);
                continue;
            }
            EXTRA_HITS.put(id, left - 1);
        }
    }

    private static void prepareLethalState(LivingEntity target, DamageSource src) {
        if (target.isSleeping()) {
            target.stopSleeping();
        }
        target.getCombatTracker().recordDamage(src, KILL_DAMAGE);
        target.setHealth(0.0F);
        target.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ENTITY_DAMAGE);
    }

    /**
     * 「正常死亡」兜底结算：当 {@code die()} 因为模组自身的无敌/阶段逻辑没有真正生效时，
     * 手工重放原版的死亡收尾。照 ae2lt 的 {@code OverloadExecutionService#forceDie}。
     *
     * <p>关键点：模组（如龙之研究的混沌守卫）判断「是否正常死亡」看的是
     * **掉落 + 击杀统计 + ENTITY_DIE 事件**，而不是实体有没有被移除。</p>
     */
    private static boolean settleNormalDeath(LivingEntity target, DamageSource src) {
        if (target.isRemoved() || ((LivingEntityAccessor) target).shizuku$isDead()) {
            return false;
        }
        try {
            LivingEntity killer = src.getEntity() instanceof LivingEntity le ? le : target.getKillCredit();
            int score = ((LivingEntityAccessor) target).shizuku$getDeathScore();
            if (score >= 0 && killer != null) {
                killer.awardKillScore(target, score, src);
            }
            if (target.isSleeping()) {
                target.stopSleeping();
            }
            ((LivingEntityAccessor) target).shizuku$setDead(true);
            target.getCombatTracker().recheckStatus();
            if (target.level() instanceof ServerLevel level) {
                var sourceEntity = src.getEntity();
                if (sourceEntity != null) {
                    // 击杀统计/成就（返回值不参与判定：某些模组 boss 的 killedEntity 会返回 false）
                    sourceEntity.killedEntity(level, target);
                }
                target.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ENTITY_DIE);
                // 关键：模组自己的 die() 被无敌/阶段逻辑吞掉时，这里直接结算原版掉落
                ((LivingEntityAccessor) target).shizuku$dropAllDeathLoot(level, src);
                level.broadcastEntityEvent(target, (byte) 3);
            }
            target.setPose(net.minecraft.world.entity.Pose.DYING);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[SA/settle] 失败 {}: {}", target.getClass().getSimpleName(), t.toString());
            return false;
        }
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
                    ResourceLocation.fromNamespaceAndPath("shizuku", "ymsa_kill"));
            return level.damageSources().source(key, attacker, attacker);
        } catch (Exception e) {
            LOGGER.debug("[SA] 自定义伤害类型 shizuku:ymsa_kill 不可用, 回退 genericKill: {}", e.toString());
            return level.damageSources().genericKill();
        }
    }

    private static KillOutcome killTarget(LivingEntity target, ServerPlayer player, ServerLevel level, boolean wipe) {
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
            // Unsafe 层（与上游一致）：先把无敌/受击窗口清零，过载时再把 DATA_HEALTH_ID 直写成 0，
            // 绕开 setHealth() 的 clamp(0, maxHealth) —— 血量锁 / 无限血 Boss 也吃这一刀。
            JudgmentExecution.breakInvulnerability(target);
            if (JudgmentExecution.armed()) {
                JudgmentExecution.fakeDeathState(target);
            }
            target.setLastHurtByMob(player);
            target.hurt(src, KILL_DAMAGE);
            // 不管 hurt 返回什么，只要还没真正死，就走「正常死亡」链路：
            // 混沌守卫这类在无敌阶段会把伤害吞掉（hurt 可能返回 true 但血量不动），
            // 之前只在 hurt 被拒时兜底，于是它被后面的 discard() 直接抹掉 —— 表现为「直接消失」。
            if (!((LivingEntityAccessor) target).shizuku$isDead()) {
                prepareLethalState(target, src);
                target.die(src);
            }
            boolean settled = false;
            if (!target.isRemoved() && !((LivingEntityAccessor) target).shizuku$isDead()) {
                // 模组仍没认这次死亡 → 手工重放原版死亡收尾（掉落/击杀统计/ENTITY_DIE/死亡动画）
                settled = settleNormalDeath(target, src);
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

            outcome.killed = true;
            BossCompat.postSettle(target);
            boolean modded = !target.getClass().getName().startsWith("net.minecraft.");
            LOGGER.info("[SA/killTarget] {} settled={} modded={} dead={} removed={} hp={}",
                    target.getClass().getSimpleName(), settled, modded,
                    ((LivingEntityAccessor) target).shizuku$isDead(), target.isRemoved(), target.getHealth());
            boolean deadFlag = ((LivingEntityAccessor) target).shizuku$isDead();
            boolean dying = target.isDeadOrDying() || deadFlag;
            // 解放 + Shift：掉落和经验会被一并清掉，实体也就没必要保留死亡动画 → 直接抹掉。
            if (wipe) {
                BossCompat.tryErase(target, player);
                forceErase(target, level);
                if (isBossLike(target)) {
                    broadcastClientErase(target, level, false);
                }
                if (level.getEntity(target.getId()) == target) {
                    outcome.annihilated = annihilate(target, player, level);
                }
                if (level.getEntity(target.getId()) == target && !target.isRemoved()) {
                    FORCE_WIPE.put(target.getUUID(), 2);
                    FORCE_WIPE_CASTER.put(target.getUUID(), player.getUUID());
                    if (JUDGMENT_DEBUG) LOGGER.info("[SA] 抹除未生效，排入重试: {}", target.getClass().getSimpleName());
                }
                return outcome;
            }
            // 没一次打穿（护盾/受击冷却/阶段保护）→ 统一排补刀，不区分是不是 boss
            if (!deadFlag && !target.isDeadOrDying()) {
                EXTRA_HITS.put(target.getUUID(), 5);
                EXTRA_HITS_SRC.put(target.getUUID(), src);
            }
            // ① 纯功能实体（训练假人）→ 立即收尾
            if (isUtilityEntity(target)) {
                settleNormalDeath(target, src);
                forceErase(target, level);
                if (level.getEntity(target.getId()) == target) {
                    outcome.annihilated = annihilate(target, player, level);
                }
                return outcome;
            }
            // ② 其余一律不管：die() 已调用，真死了的会自己走到动画那一步并移除
            return outcome;
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
        CompoundTag data = target.getPersistentData();
        for (String tag : PROTECTION_TAGS) {
            data.remove(tag);
        }
    }

    private static void suppressRespawn(LivingEntity target, ServerPlayer player) {
        RESPAWN_TRACKER.put(target.getUUID(), new YmsaLogic.CleanupEntry(player.getUUID(), RESPAWN_TRACK_TICKS));
        if (isBossLike(target)) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
            if (key != null) {
                TYPE_SUPPRESSION.put(key.toString(), BOSS_SUPPRESS_TICKS);
            }
        }
    }

    /** 纯功能实体（dummmmmmy 训练假人）：掉完物品不会自己消失，SA 打中后直接收尾。 */
    private static boolean isUtilityEntity(LivingEntity entity) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null && "dummmmmmy".equals(key.getNamespace());
    }

    private static boolean isBossLike(LivingEntity entity) {
        boolean byKeywords = YmsaLogic.matchesBossKeywordsPure(
                entity.getClass().getName().toLowerCase(),
                entity.getName().getString().toLowerCase(),
                CLASS_BOSS_FRAGMENTS, NAME_BOSS_FRAGMENTS);
        return byKeywords || entity.getMaxHealth() > HIGH_HP_THRESHOLD || !isMinecraftNamespace(entity);
    }

    private static boolean isMinecraftNamespace(LivingEntity entity) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
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

            target.kill();
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
        if (target.isMultipartEntity()) {
            try {
                level.getPartEntities().removeIf(part -> part.getParent() == target);
            } catch (Exception ignored) {
            }
        }

        PENDING_WIPE.put(target.getUUID(), target);
    }

    private static void broadcastClientErase(LivingEntity target, ServerLevel level, boolean typeKill) {
        try {
            int[] entityIds;
            if (target.isMultipartEntity()) {
                net.neoforged.neoforge.entity.PartEntity<?>[] parts = target.getParts();
                entityIds = new int[1 + parts.length];
                entityIds[0] = target.getId();
                for (int i = 0; i < parts.length; i++) {
                    entityIds[i + 1] = parts[i].getId();
                }
            } else {
                entityIds = new int[]{target.getId()};
            }

            boolean[] typeKills = new boolean[entityIds.length];
            Arrays.fill(typeKills, typeKill);

            // 原版移除包由 removeEntity 广播；再发自定义包执行客户端强制抹除。
            UUID[] uuids = new UUID[entityIds.length];
            uuids[0] = target.getUUID();
            if (target.isMultipartEntity()) {
                net.neoforged.neoforge.entity.PartEntity<?>[] parts = target.getParts();
                for (int i = 0; i < parts.length; i++) {
                    uuids[i + 1] = parts[i].getUUID();
                }
            }

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

    public static void handleServerTickEnd(MinecraftServer server) {
        // 解放（能力解放）状态下连击 tick 会被跳过，审判时间轴由这里驱动：
        // age 1 → beginJudgment / 8 → openRift / 42 → doYmsaSlash / 54 → 收刀音效。
        // 漏掉这一行 ⇒ 二三段动画不出现、且解放状态下完全打不出伤害。
        UnleashedJudgment.tick(server);
        processExtraHits(server);
        processForceWipe(server);
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
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
            return key != null && TYPE_SUPPRESSION.containsKey(key.toString());
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        handleServerTickEnd(event.getServer());
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (shouldCancelJoin(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof LivingEntity living && isTickSuppressed(living)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        handleDropsProcessed(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearPlayerState(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayerState(event.getEntity().getUUID());
    }

    private static final class KillOutcome {
        boolean killed;
        boolean forcedDrops;
        boolean annihilated;
    }

    /** 施法瞬间锁定的朝向与眼睛位置（yaw, pitch, x, y, z）。 */
    /** 「按 Shift 抹除」但 remove() 没生效的实体：uuid → {剩余重试 tick, 是否允许最底层手段}。 */
    private static final java.util.Map<UUID, Integer> FORCE_WIPE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<UUID, UUID> FORCE_WIPE_CASTER = new java.util.concurrent.ConcurrentHashMap<>();

    /** 顽固 boss 的补刀队列：uuid → 剩余次数。 */
    private static final java.util.Map<UUID, Integer> EXTRA_HITS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<UUID, DamageSource> EXTRA_HITS_SRC = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<java.util.UUID, double[]> AIM = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 施法瞬间是否按住 Shift（决定这次 SA 是否清理掉落物）。
     *
     * <p>原版语义：**释放那一刻**按住 Shift 就清理，之后松不松手都照清。
     * 之前是等到 42 tick 落剑时才实时读 Shift，导致「动画中途松手就不清了」。</p>
     */
    private static final java.util.Map<java.util.UUID, Boolean> CLEANUP_LATCH = new java.util.concurrent.ConcurrentHashMap<>();

    public static void beginJudgment(net.minecraft.server.level.ServerPlayer player) {
        CLEANUP_LATCH.put(player.getUUID(), player.isShiftKeyDown());
        AIM.put(player.getUUID(), new double[]{player.getYRot(), player.getXRot(),
                player.getEyePosition().x, player.getEyePosition().y, player.getEyePosition().z});
        broadcastPhase(player, 1);
    }

    public static void openRift(net.minecraft.server.level.ServerPlayer player) {
        protectMainHandBlade(player);
        broadcastPhase(player, STRIKE_TICK);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.TRIDENT_THROW.value(),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.72F);
    }

    public static void verdictTick(net.minecraft.server.level.ServerPlayer player) {
        broadcastPhase(player, VERDICT_TICK);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.BELL_RESONATE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.4F, 0.65F);
    }

    /** 按锁定的朝向 + 位置发包；没有锁定值就回退当前值。 */
    private static void broadcastPhase(net.minecraft.server.level.ServerPlayer player, int phaseTick) {
        // 与上游一致：阶段 1 只是服务端标记，不必发包
        if (phaseTick == 1) return;
        double[] aim = AIM.get(player.getUUID());
        com.buwan.shizuku.network.PacketHandler.sendToNearby(
                aim == null
                        ? com.buwan.shizuku.network.JudgmentImpactPacket.from(player, phaseTick)
                        : com.buwan.shizuku.network.JudgmentImpactPacket.from(player, phaseTick,
                                (float) aim[0], (float) aim[1], aim[2], aim[3], aim[4]),
                player, DOMAIN_RADIUS * 2.5);
    }

    public static void clearUnleashedArea(java.util.UUID id) {
        CLEANUP_LATCH.remove(id);
        AIM.remove(id);
    }
    public static void finishChaosEncounter(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity boss) {
        if (boss instanceof net.minecraft.world.entity.LivingEntity living) living.setHealth(0.0F);
    }

    public static boolean isChaosWither(net.minecraft.world.entity.Entity entity) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null && "chaoswither".equals(key.getNamespace());
    }
    public static boolean isUnleashedBoss(net.minecraft.world.entity.LivingEntity entity) {
        return isChaosWither(entity) || entity.getType().is(BOSS_TYPES);
    }
    public static boolean blocksChaosSpawn(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 pos) { return false; }
    public static boolean blocksUnleashedJoin(net.minecraft.world.entity.Entity entity) { return false; }
}
