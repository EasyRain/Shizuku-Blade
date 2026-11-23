package com.buwan.shizuku.sa;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 模组 boss 的「专属针对」注册表。
 *
 * <p>处理顺序（killTarget 里）：</p>
 * <ol>
 *   <li>按 Shift → 直接抹除（不问类型）</li>
 *   <li>本表的专属处理（例如超级史蒂夫只能走它自己的 {@code SSUtil.killEntity}）</li>
 *   <li>都没命中 → 走**通用逻辑**：hurt → prepareLethalState → die →
 *       （必要时）settleNormalDeath，且模组实体不强制移除、一次打不穿就排补刀队列</li>
 * </ol>
 *
 * <p>也就是说：**类型针对只处理「通用逻辑打不动」的**，其余一律交给通用逻辑 ——
 * 这样新增模组 boss 不用改代码，除非它的血量/死亡完全不走原版。</p>
 *
 * <h2>原作者的全部「针对」清单（本类即总入口）</h2>
 * <table border="1">
 *   <tr><th>mod</th><th>处理</th><th>在本类中的位置</th></tr>
 *   <tr><td><b>supersteve</b></td><td>反射 {@code SSUtil.killEntity}（血量走自家 ssHealth 状态机）</td>
 *       <td>条目 1：{@code death} 钩子（{@link #trySpecial} / {@link #tryErase}）</td></tr>
 *   <tr><td><b>chaoswither</b></td><td>遭遇结束标志 {@code goneToWorld} 复位、阻止生成</td>
 *       <td>条目 2：{@code post} 钩子（{@link #postSettle}）＋ {@code Ymsa.blocksChaosSpawn}</td></tr>
 *   <tr><td>goety（Apollyon）</td><td>百分比伤害的豁免判定</td>
 *       <td>不属死亡入口，仍在 {@code SummerSlash.isRestrictedBoss}</td></tr>
 *   <tr><td>omnimobs（flashfur）</td><td>清残留场对象</td>
 *       <td>不属死亡入口，仍在 {@code Ymsa.sweepBossObjects} / {@code FlashfurRemove}</td></tr>
 *   <tr><td>Draconic（混沌守卫）</td><td>作者未处理；靠通用逻辑（tickDeath + 保持实体）</td>
 *       <td>无需条目</td></tr>
 * </table>
 *
 * <p>⚠️ {@code chaoswither} 只发布了 1.7.10 / 1.12.2，1.20.1 装不到 ⇒ 该条目当前是死代码，保留待用。</p>
 */
public final class BossCompat {

    /**
     * 一个专属处理条目。
     *
     * @param death 死亡入口（返回 true 表示已由 mod 自己处理，不再走通用逻辑）；可为 null
     * @param post  死亡/抹除之后的收尾（如复位 mod 的遭遇标志）；可为 null
     */
    private record Entry(String id, Predicate<LivingEntity> matches,
                         BiPredicate<LivingEntity, ServerPlayer> death,
                         java.util.function.Consumer<LivingEntity> post) {}

    private static final List<Entry> ENTRIES = List.of(
            // 超级史蒂夫：血量走自己的 ssHealth 状态机，原版 hurt 打不动；
            // 它提供的 SSUtil.killEntity 是客户端移除 API（不掉专属剑，属于 mod 自身限制）。
            new Entry("supersteve", SuperSteveCompat::isBoss,
                    (entity, player) -> SuperSteveCompat.execute(entity), null),
            // 混沌凋灵：死亡后要把 mod 的遭遇标志复位，否则它会一直请求重新生成 boss。
            // （1.20.1 无此 mod ⇒ 当前不会命中，保留待社区移植版）
            new Entry("chaoswither", Ymsa::isChaosWither,
                    null,
                    entity -> {
                        if (entity.level() instanceof net.minecraft.server.level.ServerLevel level) {
                            Ymsa.finishChaosEncounter(level, entity);
                        }
                    })
    );

    private BossCompat() {}

    /**
     * 尝试走专属处理。
     *
     * @return true 表示已处理，调用方不要再走通用逻辑
     */
    public static boolean trySpecial(LivingEntity target, ServerPlayer player) {
        for (Entry entry : ENTRIES) {
            if (!entry.matches().test(target)) continue;
            if (entry.death() == null) continue;
            return entry.death().test(target, player);
        }
        return false;
    }

    /**
     * 抹除（按 Shift）时的专属预处理。
     *
     * <p>抹除阶段会跳过 {@link #trySpecial}（因为不需要战利品），但有的模组实体光靠
     * remove/discard 抹不掉（例如超级史蒂夫的 remove 被状态机接管）——
     * 这里让它们先走一遍自己的“消失”入口，再交给通用抹除。</p>
     */
    public static void tryErase(LivingEntity target, ServerPlayer player) {
        for (Entry entry : ENTRIES) {
            if (entry.matches().test(target) && entry.death() != null) {
                entry.death().test(target, player);
                return;
            }
        }
    }

    /** 死亡/抹除之后的收尾钩子（例如混沌凋灵的遭遇标志复位）。 */
    public static void postSettle(LivingEntity target) {
        for (Entry entry : ENTRIES) {
            if (entry.post() == null || !entry.matches().test(target)) continue;
            try {
                entry.post().accept(target);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 该目标是否有专属处理（仅用于日志/调试）。 */
    public static boolean hasSpecial(LivingEntity target) {
        for (Entry entry : ENTRIES) {
            if (entry.matches().test(target)) return true;
        }
        return false;
    }
}
