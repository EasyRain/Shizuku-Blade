package com.buwan.shizuku.sa;

import com.buwan.shizuku.core.JudgmentBridge;
import com.buwan.shizuku.core.UnsafeAccess;
import com.buwan.shizuku.mixin.LivingEntityAccessor;
import java.lang.reflect.Method;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.LivingEntity;

/**
 * 审判解放的内存级处决层（第一层：Unsafe 裸内存改写）。
 *
 *
 * 所有写入都用 putXxxVolatile + 尾部 fullFence：这既保证了跨线程可见性，
 * 也让 C2 无法把写在寄存器里的旧血量继续用下去 —— 这就是"对抗 JIT"的那一半，
 * 另一半在 agent 层的 retransform 与 -XX:CompileCommand。
 */
public final class JudgmentExecution {
    // ---- LivingEntity ----
    private static final long OFF_DEAD = UnsafeAccess.offsetOf(LivingEntity.class, "dead", "f_20890_");
    private static final long OFF_DEATH_TIME = UnsafeAccess.offsetOf(LivingEntity.class, "deathTime", "f_20919_");
    private static final long OFF_HURT_TIME = UnsafeAccess.offsetOf(LivingEntity.class, "hurtTime", "f_20916_");
    private static final long OFF_HURT_DURATION = UnsafeAccess.offsetOf(LivingEntity.class, "hurtDuration", "f_20917_");
    // ---- Entity ----
    private static final long OFF_INVULNERABLE_TIME = UnsafeAccess.offsetOf(Entity.class, "invulnerableTime", "f_19802_");
    private static final long OFF_INVULNERABLE = UnsafeAccess.offsetOf(Entity.class, "invulnerable", "f_19840_");
    private static final long OFF_REMOVAL_REASON = UnsafeAccess.offsetOf(Entity.class, "removalReason", "f_146795_");
    private static final long OFF_TICK_COUNT = UnsafeAccess.offsetOf(Entity.class, "tickCount", "f_19797_");
    // ---- SynchedEntityData ----
    private static final long OFF_ITEMS_BY_ID = UnsafeAccess.offsetOf(SynchedEntityData.class, "itemsById", "f_135345_");
    // ---- SynchedEntityData$DataItem ----
    private static final long OFF_DATA_VALUE = resolveDataItemValueOffset();
    private static final Method INT_MAP_GET = resolveIntMapGet();

    private JudgmentExecution() {}

    private static long resolveDataItemValueOffset() {
        try {
            Class<?> item = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataItem");
            return UnsafeAccess.offsetOf(item, "value", "f_135391_");
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static Method resolveIntMapGet() {
        try {
            return Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectMap").getMethod("get", int.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Unsafe 与关键偏移都就绪时才算可用；否则调用方回退到官方 API 路径。 */
    public static boolean supported() {
        return UnsafeAccess.available() && OFF_DEAD >= 0L && OFF_ITEMS_BY_ID >= 0L && OFF_DATA_VALUE >= 0L && INT_MAP_GET != null;
    }

    public static String describe() {
        return "unsafe=" + UnsafeAccess.available()
            + " dead=" + OFF_DEAD
            + " deathTime=" + OFF_DEATH_TIME
            + " hurtTime=" + OFF_HURT_TIME
            + " invulnTime=" + OFF_INVULNERABLE_TIME
            + " invuln=" + OFF_INVULNERABLE
            + " removal=" + OFF_REMOVAL_REASON
            + " itemsById=" + OFF_ITEMS_BY_ID
            + " dataValue=" + OFF_DATA_VALUE
            + " bridge[" + JudgmentBridge.describe() + "]";
    }

    /**
     * 直接写 DATA_HEALTH_ID 的 DataItem.value。
     * 不经过 setHealth，所以不做 0..maxHealth 的 clamp —— 传 0 就是真的 0，
     * 传负数也能写进去（让 isDeadOrDying 的 <= 0 判断稳稳成立）。
     */
    public static boolean writeHealthDirect(LivingEntity target, float value) {
        if (target == null || !supported()) return false;
        try {
            SynchedEntityData data = target.getEntityData();
            Object map = UnsafeAccess.getObject(data, OFF_ITEMS_BY_ID);
            if (map == null) return false;
            EntityDataAccessor<Float> healthId = LivingEntityAccessor.ymsa$getDataHealthId();
            Object item = INT_MAP_GET.invoke(map, healthId.getId());
            if (item == null) return false;
            boolean ok = UnsafeAccess.setObject(item, OFF_DATA_VALUE, Float.valueOf(value));
            UnsafeAccess.fullFence();
            return ok;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 清掉无敌/受击窗口，让任何后续伤害都能落上去。 */
    public static void breakInvulnerability(LivingEntity target) {
        if (target == null) return;
        UnsafeAccess.setInt(target, OFF_INVULNERABLE_TIME, 0);
        UnsafeAccess.setInt(target, OFF_HURT_TIME, 0);
        UnsafeAccess.setInt(target, OFF_HURT_DURATION, 0);
        UnsafeAccess.setBoolean(target, OFF_INVULNERABLE, false);
        UnsafeAccess.storeFence();
    }

    /**
     * 内存级处决：把血量写成 0，并把死亡计时推到终点。
     * 任何一个环节失败都返回 false，让上层继续走 hurt/die 的常规链路。
     */
    public static boolean hardKill(LivingEntity target) {
        if (target == null) return false;
        boolean wrote = writeHealthDirect(target, 0.0F);
        breakInvulnerability(target);
        boolean dead = UnsafeAccess.setBoolean(target, OFF_DEAD, true);
        UnsafeAccess.setInt(target, OFF_DEATH_TIME, 20);
        // 让 isRemoved()/getRemovalReason() 立刻为真，跳过原版死亡动画窗口
        UnsafeAccess.setObject(target, OFF_REMOVAL_REASON, RemovalReason.KILLED);
        UnsafeAccess.fullFence();
        return wrote || dead;
    }

    /**
     * 把实体彻底从内存层面"打成死物"，但不动 removalReason：
     * 用于那些覆写了 die()/isDeadOrDying() 的 Boss —— 先让它自己的逻辑认为已经死了。
     */
    public static boolean fakeDeathState(LivingEntity target) {
        if (target == null) return false;
        writeHealthDirect(target, 0.0F);
        breakInvulnerability(target);
        UnsafeAccess.setBoolean(target, OFF_DEAD, true);
        UnsafeAccess.setInt(target, OFF_DEATH_TIME, 20);
        UnsafeAccess.fullFence();
        return true;
    }

    /** 把 tickCount 归零后大幅推进，用于触发依赖存活时间的自毁逻辑。 */
    public static void ageOut(LivingEntity target, int ticks) {
        if (target == null) return;
        UnsafeAccess.setInt(target, OFF_TICK_COUNT, ticks);
        UnsafeAccess.storeFence();
    }

    public static void arm() {
        JudgmentBridge.arm(true);
    }

    public static void disarm() {
        JudgmentBridge.arm(false);
    }

    public static boolean armed() {
        return JudgmentBridge.armed();
    }
}
