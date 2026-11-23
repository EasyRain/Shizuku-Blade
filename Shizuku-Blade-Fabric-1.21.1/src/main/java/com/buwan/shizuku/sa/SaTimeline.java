package com.buwan.shizuku.sa;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 我们自己的剑技（SA）时间轴。
 *
 * <p><b>为什么不用 SlashBlade 的 {@code ComboState.TimeLineTickAction}：</b>
 * 它靠 {@code ComboState.getElapsed(entity)}（= 当前 gameTime − 刀的 lastActionTime）比对 tick。
 * 而 SlashBlade-Refabricated **2.0.5** 新增了 motion 广播管线
 * （{@code BladeRuntimeSyncer} / {@code MotionBroadcastPacket} / {@code applyComboSeq}），
 * 在服务端持续改写 {@code comboSeq} 与 {@code lastActionTime} —— 实测：
 * 我们设的连招状态 5 tick 后就被改回 {@code slashblade:none}，elapsed 每 ~21 tick 归零一次。
 * 于是 {@code put(7/10/13/16/19, ...)} 这类延后动作永远等不到。</p>
 *
 * <p>本类把「第几个 tick 做什么」交给**自己的 tick 循环**：
 * 连招状态每 tick 调一次 {@link #getBuilder()} 产出的 consumer（只在每次施法第一次生效），
 * 之后由 {@link #tickServer(MinecraftServer)} / {@link #tickClient(Minecraft)} 推进。
 * 这样与前置库的状态寿命、elapsed 是否被改写完全无关。</p>
 *
 * <p>取消条件（沿用原版手感）：死亡 / 切主手槽位 / 换掉手里的物品 / 掉线。</p>
 */
public final class SaTimeline {

    /** 一个时间点的动作。 */
    @FunctionalInterface
    public interface Step {
        void run(LivingEntity entity);
    }

    /** 一次施法的计划。 */
    private static final class Plan {
        final Map<Integer, Step> steps;
        final long base;
        final ItemStack blade;
        final int slot;
        final Set<Integer> fired = ConcurrentHashMap.newKeySet();
        final int maxTick;

        Plan(Map<Integer, Step> steps, long base, ItemStack blade, int slot) {
            this.steps = steps;
            this.base = base;
            this.blade = blade;
            this.slot = slot;
            int max = 0;
            for (int tick : steps.keySet()) {
                max = Math.max(max, tick);
            }
            this.maxTick = max;
        }
    }

    /** 客户端与服务端各一份（单人游戏同 JVM，不能混用，否则同一动作会执行两次）。 */
    private static final Map<UUID, Plan> SERVER_PLANS = new ConcurrentHashMap<>();
    private static final Map<UUID, Plan> CLIENT_PLANS = new ConcurrentHashMap<>();

    private SaTimeline() {}

    /** 与 {@code ComboState.TimeLineTickAction.getBuilder()} 同形的构造器，方便状态里直接替换。 */
    public static Builder getBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private final TreeMap<Integer, Step> steps = new TreeMap<>();

        private Builder() {}

        public Builder put(int tick, Step step) {
            steps.put(tick, step);
            return this;
        }

        public Consumer<LivingEntity> build() {
            Map<Integer, Step> plan = Map.copyOf(steps);
            return entity -> schedule(entity, plan, null);
        }
    }

    /**
     * 按「库的 elapsed」推算本次施法起点后登记（连招状态路径用）。
     *
     * @param entity 施法者
     * @param steps  时间点 → 动作
     */
    public static void plan(LivingEntity entity, Map<Integer, Step> steps) {
        schedule(entity, steps, null);
    }

    /** 明确指定起点（解放路径在松手那一刻调用）。 */
    public static void start(LivingEntity entity, Map<Integer, Step> steps) {
        schedule(entity, steps, 0L);
    }

    private static void schedule(LivingEntity entity, Map<Integer, Step> steps, Long explicitAge) {
        if (entity == null || steps.isEmpty()) return;
        boolean client = entity.level().isClientSide();
        Map<UUID, Plan> plans = client ? CLIENT_PLANS : SERVER_PLANS;
        long now = entity.level().getGameTime();
        long base = explicitAge != null
                ? now - explicitAge
                : now - mods.flammpfeil.slashblade.registry.combo.ComboState.getElapsed(entity);
        UUID id = entity.getUUID();
        Plan prev = plans.get(id);
        // 同一次施法会被状态每 tick 重复登记：用「同一张步骤表 + 同一起点」去重
        if (prev != null && prev.steps == steps && prev.base == base) {
            return;
        }
        plans.put(id, new Plan(steps, base, entity.getMainHandItem(), slotOf(entity)));
    }

    /** 取消某位玩家当前的时间轴（切刀、断线、关解放）。 */
    public static void cancel(UUID id) {
        SERVER_PLANS.remove(id);
        CLIENT_PLANS.remove(id);
    }

    /** 服务端每 tick 调用。 */
    public static void tickServer(MinecraftServer server) {
        if (SERVER_PLANS.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (Map.Entry<UUID, Plan> entry : List.copyOf(SERVER_PLANS.entrySet())) {
            advance(server.getPlayerList().getPlayer(entry.getKey()),
                    entry.getValue(), now, entry.getKey(), SERVER_PLANS);
        }
    }

    /** 客户端每 tick 调用（单人游戏下与服务端互不影响）。 */
    public static void tickClient(Minecraft minecraft) {
        if (CLIENT_PLANS.isEmpty() || minecraft.level == null) return;
        long now = minecraft.level.getGameTime();
        for (Map.Entry<UUID, Plan> entry : List.copyOf(CLIENT_PLANS.entrySet())) {
            advance(minecraft.level.getPlayerByUUID(entry.getKey()),
                    entry.getValue(), now, entry.getKey(), CLIENT_PLANS);
        }
    }

    private static void advance(LivingEntity entity, Plan plan, long now, UUID id, Map<UUID, Plan> plans) {
        if (entity == null || !entity.isAlive()) {
            plans.remove(id);
            return;
        }
        // 与库无关的取消条件：切槽位 / 换物品
        if (slotOf(entity) != plan.slot || !ItemStack.isSameItem(entity.getMainHandItem(), plan.blade)) {
            plans.remove(id);
            return;
        }
        long age = now - plan.base;
        if (age < 0) {
            plans.remove(id);
            return;
        }
        for (Map.Entry<Integer, Step> step : plan.steps.entrySet()) {
            int tick = step.getKey();
            if (age >= tick && plan.fired.add(tick)) {
                step.getValue().run(entity);
            }
        }
        // 注意：这里**故意不删**计划。
        // 连招状态每 tick 都会来登记；如果走完就删，下一次登记会生成新计划（fired 清空），
        // 那些已经过去的 tick 会再执行一遍 → 重复结算。计划只在「换手/换刀/死亡/新施法」时被替换。
    }

    private static int slotOf(LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player player
                ? player.getInventory().selected
                : -1;
    }
}
