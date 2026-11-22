package com.buwan.shizuku.sa;

import com.buwan.shizuku.network.UnleashedInputPacket.Action;
import com.buwan.shizuku.registry.SAComboRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public final class UnleashedJudgment {
    private record Use(ServerPlayer player, ItemStack blade, long start, boolean casting) {}
    private static final Map<UUID, Use> uses = new HashMap<>();
    private static final Set<UUID> enabled = new HashSet<>();
    private static final Set<UUID> damagePlayers = new HashSet<>();
    private UnleashedJudgment() {}

    public static boolean isBlade(ItemStack stack) {
        return stack.getItem() instanceof ItemSlashBlade && stack.getCapability(ItemSlashBlade.BLADESTATE)
            .map(state -> "item.shizuku.ymblade".equals(state.getTranslationKey())).orElse(false);
    }

    /**
     * 解放能力的开启权限：服务器 OP（权限等级 >= 2）。
     *
     * 单人游戏里开了作弊的玩家自己是 OP，本地测试不受影响；
     * 多人服务器上普通玩家无论把客户端配置怎么改，都过不了这一关。
     */
    static boolean canUnleash(ServerPlayer player) {
        return player != null && player.hasPermissions(2);
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
                player.displayClientMessage(Component.translatable("message.shizuku.judgment.op_only"), true);
                enabled.remove(id);
                return;
            }
            enabled.add(id);
            var blade = player.getMainHandItem();
            if (!uses.containsKey(id) && isBlade(blade) && blade.hasTag() && blade.getTag().hasUUID("shizuku:cast_blade")) {
                var data = UnleashedVerdicts.get(player.serverLevel().getServer());
                int age = data.savedAge(id, blade.getTag().getUUID("shizuku:cast_blade"));
                if (age >= 0 && age < Ymsa.END_TICK && player.isAlive() && !player.isSpectator())
                    uses.put(id, new Use(player, blade, player.serverLevel().getServer().overworld().getGameTime() - age, true));
            }
            return;
        }
        if (action == Action.DISABLE) {
            Ymsa.clearUnleashedArea(id);
            UnleashedVerdicts.get(player.serverLevel().getServer()).clearCaster(id);
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
            blade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
                if (now - use.start < state.getFullChargeTicks(player)) return;
                var tag = blade.getOrCreateTag();
                if (!tag.hasUUID("shizuku:cast_blade")) tag.putUUID("shizuku:cast_blade", UUID.randomUUID());
                UnleashedVerdicts.get(player.serverLevel().getServer()).beginCast(id, tag.getUUID("shizuku:cast_blade"));
                uses.put(id, new Use(player, blade, now, true));
                state.updateComboSeq(player, SAComboRegistry.YMSA.getId());
            });
        }
    }

    private static long serverClock(ServerPlayer player) {
        return player.serverLevel().getServer().overworld().getGameTime();
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
        return world.isClientSide && ClientCoverage.contains(world, pos);
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
            if (!UnleashedVerdicts.get(server).advance(player.getUUID(), (int)age)) continue;
            if (age == 1) Ymsa.beginJudgment(player);
            if (age == Ymsa.STRIKE_TICK) Ymsa.openRift(player);
            if (age == Ymsa.VERDICT_TICK) Ymsa.doYmsaSlash(player);
            if (age == Ymsa.SHEATH_TICK) AttackManager.playQuickSheathSoundAction(player);
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
        return damagePlayers.contains(player.getUUID()) && player.serverLevel().getServer().isPvpAllowed()
            && isBlade(player.getMainHandItem());
    }
}
