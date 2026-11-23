package com.buwan.shizuku.sa;

import com.buwan.shizuku.network.UnleashedInputPacket.Action;
import com.buwan.shizuku.registry.SAComboRegistry;
import com.buwan.shizuku.util.ItemNbt;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public final class UnleashedJudgment {
    /**
     * 是否向附近客户端广播审判落剑包（{@code shizuku:judgment_impact}）。
     *
     * <p>2026-11-23 已恢复为 true：当时「一放审判就掉线」的真实原因不是 codec 写错，而是
     * {@code PacketHandler.register()} 漏注册了这个 payload 类型（26.1.2 移植时漏了三条），
     * 原版分发 codec 编码时抛 "unrecognized payload"，被 Fabric 包成
     * {@code EncoderException: Failed to encode packet ...}。补齐注册后编码正常。</p>
     */
    private static final boolean BROADCAST_IMPACT = true;

    private record Use(ServerPlayer player, ItemStack blade, long start, boolean casting) {}
    private static final Map<UUID, Use> uses = new HashMap<>();
    private static final Set<UUID> enabled = new HashSet<>();
    private static final Set<UUID> damagePlayers = new HashSet<>();
    /** 广播审判阶段给附近客户端（Fabric 版：发 JudgmentImpactPacket 触发动画）。 */
    static void notePhase(net.minecraft.server.level.ServerPlayer player, int phaseTick) {
        notePhase(player, phaseTick, player == null ? 0.0F : player.getYRot(), player == null ? 0.0F : player.getXRot());
    }

    /** 用指定朝向发包（审判期间锁定施法时的朝向，避免落剑跟着鼠标跑）。 */
    /** 用锁定朝向 + 锁定位置发包。 */
    static void notePhase(net.minecraft.server.level.ServerPlayer player, int phaseTick,
                          float yaw, float pitch, double ox, double oy, double oz) {
        if (player == null || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        if (phaseTick == 1) return;
        if (!BROADCAST_IMPACT) {
            return;
        }
        com.buwan.shizuku.network.PacketHandler.sendToNearby(
            com.buwan.shizuku.network.JudgmentImpactPacket.from(player, phaseTick, yaw, pitch, ox, oy, oz),
            player, Ymsa.DOMAIN_RADIUS * 2.5);
    }

    static void notePhase(net.minecraft.server.level.ServerPlayer player, int phaseTick, float yaw, float pitch) {
        if (player == null || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        if (phaseTick == 1) return;
        if (!BROADCAST_IMPACT) {
            return;
        }
        com.buwan.shizuku.network.PacketHandler.sendToNearby(
            com.buwan.shizuku.network.JudgmentImpactPacket.from(player, phaseTick, yaw, pitch),
            player, Ymsa.DOMAIN_RADIUS * 2.5);
    }

    private UnleashedJudgment() {}

    public static boolean isBlade(ItemStack stack) {
        return stack.getItem() instanceof ItemSlashBlade && CapabilitySlashBlade.getBladeState(stack)
            .map(state -> "item.shizuku.ymblade".equals(state.getTranslationKey())).orElse(false);
    }

    /**
     * 解放能力的开启权限：服务器 OP（权限等级 >= 2）。
     *
     * 单人游戏里开了作弊的玩家自己是 OP，本地测试不受影响；
     * 多人服务器上普通玩家无论把客户端配置怎么改，都过不了这一关。
     */
    static boolean canUnleash(ServerPlayer player) {
        // 26.1：Player#hasPermissions(int) 已删 → 新的权限集 API；
        // PermissionLevel.GAMEMASTERS 就是原来的 op 等级 2。
        return player != null && player.permissions().hasPermission(
                new net.minecraft.server.permissions.Permission.HasCommandLevel(
                        net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS));
    }

    public static void input(ServerPlayer player, Action action) {
        UUID id = player.getUUID();
        if (action == Action.DAMAGE_PLAYERS_ON || action == Action.DAMAGE_PLAYERS_OFF) {
            if (action == Action.DAMAGE_PLAYERS_ON) damagePlayers.add(id);
            else damagePlayers.remove(id);
            return;
        }
        if (action == Action.ENABLE) {
            // 权威判定：只有服务器 OP（权限等级 >= 2）才能开启解放。
            // 客户端那个 judgmentUnleashed 配置只是本地意愿，改了也没用 ——
            // 这里不放行，enabled 集合就不会有这个 UUID，后续所有解放逻辑全部不成立。
            if (!canUnleash(player)) {
                // 26.1：displayClientMessage(c, true) → sendOverlayMessage(c)（同为动作栏提示）。
                player.sendOverlayMessage(Component.translatable("message.shizuku.judgment.op_only"));
                enabled.remove(id);
                return;
            }
            enabled.add(id);
            var blade = player.getMainHandItem();
            if (!uses.containsKey(id) && isBlade(blade) && ItemNbt.has(blade)
                    && com.buwan.shizuku.util.NbtUuids.has(ItemNbt.get(blade), "shizuku:cast_blade")) {
                var data = UnleashedVerdicts.get(((net.minecraft.server.level.ServerLevel) player.level()).getServer());
                int age = data.savedAge(id, com.buwan.shizuku.util.NbtUuids.get(ItemNbt.get(blade), "shizuku:cast_blade"));
                if (age >= 0 && age < Ymsa.END_TICK && player.isAlive() && !player.isSpectator())
                    uses.put(id, new Use(player, blade, ((net.minecraft.server.level.ServerLevel) player.level()).getServer().overworld().getGameTime() - age, true));
            }
            return;
        }
        if (action == Action.DISABLE) {
            Ymsa.clearUnleashedArea(id);
            UnleashedVerdicts.get(((net.minecraft.server.level.ServerLevel) player.level()).getServer()).clearCaster(id);
            enabled.remove(id);
            cancelCast(id);
            return;
        }
        if (!enabled.contains(id)) return;
        Use use = uses.get(id);
        if (use != null && use.casting) return;
        if (action == Action.CANCEL) {
            uses.remove(id);
            return;
        }
        ItemStack blade = player.getMainHandItem();
        if (!player.isAlive() || player.isSpectator() || !isBlade(blade)) return;
        long now = serverClock(player);
        if (action == Action.PRESS) {
            uses.putIfAbsent(id, new Use(player, blade, now, false));
        } else if (use != null) {
            uses.remove(id);
            if (use.player != player || use.blade != blade) return;
            CapabilitySlashBlade.getBladeState(blade).ifPresent(state -> {
                if (now - use.start < state.getFullChargeTicks(player)) return;
                var tag = ItemNbt.edit(blade, t -> {});
                if (!com.buwan.shizuku.util.NbtUuids.has(tag, "shizuku:cast_blade")) {
                    com.buwan.shizuku.util.NbtUuids.put(tag, "shizuku:cast_blade", UUID.randomUUID());
                    ItemNbt.set(blade, tag);
                }
                UnleashedVerdicts.get(((net.minecraft.server.level.ServerLevel) player.level()).getServer()).beginCast(id, com.buwan.shizuku.util.NbtUuids.get(tag, "shizuku:cast_blade"));
                uses.put(id, new Use(player, blade, now, true));
                // 时间轴在这里起步（不依赖连招状态活多久）：Ymsa 的服务端 tick 循环会按
                // age >= STRIKE / VERDICT / SHEATH 推进三段
                com.buwan.shizuku.sa.SaTimeline.start(player, Ymsa.JUDGMENT_STEPS);
                state.updateComboSeq(player, SAComboRegistry.YMSA.getId());
            });
        }
    }

    private static long serverClock(ServerPlayer player) {
        return ((net.minecraft.server.level.ServerLevel) player.level()).getServer().overworld().getGameTime();
    }

    public static boolean isCasting(ServerPlayer player) {
        Use use = uses.get(player.getUUID());
        return use != null && use.casting;
    }

    static boolean holdsEnabledBlade(ServerPlayer player) {
        return enabled.contains(player.getUUID()) && isBlade(player.getMainHandItem());
    }

    public static boolean coversChaosWither(Entity boss) {
        return coversChaosPosition(boss.level(), boss.position());
    }

    public static boolean coversChaosPosition(net.minecraft.world.level.Level world, net.minecraft.world.phys.Vec3 pos) {
        if (world instanceof ServerLevel level) {
            if (enabled.isEmpty()) return false;
            for (ServerPlayer player : level.players()) {
                if (enabled.contains(player.getUUID()) && player.getBoundingBox().inflate(320).contains(pos)) {
                    return true;
                }
            }
            return false;
        }
        return world.isClientSide() && ClientCoverage.contains(world, pos);
    }

    private static final class ClientCoverage {
        static boolean contains(net.minecraft.world.level.Level world, net.minecraft.world.phys.Vec3 pos) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            return player != null && player.level() == world
                && com.buwan.shizuku.client.JudgmentVisuals.UNLEASHED.get()
                && player.getBoundingBox().inflate(320).contains(pos);
        }
    }

    public static void tick(MinecraftServer server) {
        if (uses.isEmpty()) return;
        for (Use use : java.util.List.copyOf(uses.values())) {
            ServerPlayer player = use.player;
            if (uses.get(player.getUUID()) != use) continue;
            long age = serverClock(player) - use.start;
            if (server.getPlayerList().getPlayer(player.getUUID()) != player || !player.isAlive()
                    || player.getMainHandItem() != use.blade || age < 0 || (use.casting && age >= Ymsa.END_TICK)) {
                uses.remove(player.getUUID(), use);
                if (use.casting && server.getPlayerList().getPlayer(player.getUUID()) == player)
                    UnleashedVerdicts.get(server).endCast(player.getUUID());
                continue;
            }
            if (!use.casting) continue;
            // 三段时序统一交给 Ymsa 的服务端时间轴（见 Ymsa.tickTimeline）。
            // 原来这里是 age == X 逐个比对，容易漏帧；而且这里少了 verdictTick，
            // 导致解放态「有击杀但没有落剑动画」。
            UnleashedVerdicts.get(server).advance(player.getUUID(), (int)age);
        }
    }

    static void cancelCast(UUID id) {
        uses.remove(id);
    }

    static void disconnect(UUID id) {
        damagePlayers.remove(id);
        enabled.remove(id);
        cancelCast(id);
    }

    public static void clear() {
        damagePlayers.clear();
        enabled.clear();
        uses.clear();
    }

    static boolean canDamagePlayers(ServerPlayer player) {
        return damagePlayers.contains(player.getUUID()) && ((net.minecraft.server.level.ServerLevel) player.level()).isPvpAllowed()
            && isBlade(player.getMainHandItem());
    }
}
