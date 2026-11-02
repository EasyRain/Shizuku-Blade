package com.buwan.shizuku.sa;

import com.buwan.shizuku.network.GalaxyFieldPacket;
import com.buwan.shizuku.network.PacketHandler;
import com.buwan.shizuku.util.DamageCalculator;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import com.buwan.shizuku.compat.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 星河领域（3.6.0）：按住 Shift + X 切换，持续对周围怪物造成伤害（仅 mosmicblade）。
 */
public class GalaxyField {

    public static final float RADIUS = 8.0F;
    private static final int DAMAGE_TICKS = 4;
    private static final float DAMAGE_RATIO = 0.5F;
    private static final Map<UUID, FieldData> activeFields = new ConcurrentHashMap<>();

    private GalaxyField() {
    }

    public static void toggleField(LivingEntity entity) {
        if (!entity.level().isClientSide()) {
            if (entity instanceof Player player) {
                ServerLevel level = (ServerLevel) entity.level();
                FieldData old = activeFields.remove(player.getUUID());
                if (old != null) {
                    PacketHandler.sendToNearby(new GalaxyFieldPacket(player.getUUID()), (ServerPlayer) player, 64.0);
                } else {
                    activeFields.put(player.getUUID(), new FieldData(level, player));
                    PacketHandler.sendToNearby(
                            new GalaxyFieldPacket(player.getUUID(), player.position(), RADIUS, -1),
                            (ServerPlayer) player, 64.0);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            // 原版是紫水晶方块的风铃音（SRG f_144243_ = AMETHYST_BLOCK_CHIME）
                            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 0.7F);
                }
            }
        }
    }

    
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!activeFields.isEmpty()) {
            activeFields.entrySet().removeIf(entry -> {
                FieldData data = entry.getValue();
                if (data.owner != null && data.owner.isAlive() && data.owner.level() == data.level
                        && isHoldingMosmicBlade(data.owner)) {
                    data.damageTicks++;
                    if (data.damageTicks % DAMAGE_TICKS == 0) {
                        applyDamage(data);
                    }
                    return false;
                } else {
                    if (data.owner instanceof ServerPlayer sp) {
                        PacketHandler.sendToNearby(new GalaxyFieldPacket(entry.getKey()), sp, 64.0);
                    }
                    return true;
                }
            });
        }
    }

    private static boolean isHoldingMosmicBlade(Player player) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ItemSlashBlade)) {
            return false;
        }
        return CapabilitySlashBlade.BLADESTATE.maybeGet(held)
                .map(s -> "item.shizuku.mosmicblade".equals(s.getTranslationKey())).orElse(false);
    }

    private static void applyDamage(FieldData data) {
        ServerLevel level = data.level;
        double cx = data.owner.getX();
        double cy = data.owner.getY();
        double cz = data.owner.getZ();
        var targets = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(cx - RADIUS, cy - 4.0, cz - RADIUS, cx + RADIUS, cy + 8.0, cz + RADIUS),
                e -> e != data.owner && !(e instanceof Player) && e.isAlive() && !e.isRemoved()
                        && e.distanceToSqr(cx, e.getY(), cz) <= RADIUS * RADIUS
        );
        if (!targets.isEmpty()) {
            // 伤害数值沿用 1.20.1 原版公式：refined × 0.5，不设封顶。
            // 伤害源不能再用 fellOutOfWorld —— 实验假人 mod 在 hurt() 里对该来源
            // 直接 remove()（无伤害、无掉落），会导致假人原地消失。
            // 改用 magic()：同样无视护甲、不产生仇恨，且假人可正常吸收并显示伤害。
            float damage = DamageCalculator.calculateRefinedDamage(data.owner, 1.0F) * DAMAGE_RATIO;
            for (LivingEntity target : targets) {
                target.invulnerableTime = 0;
                target.hurt(target.damageSources().magic(), damage);
                target.setLastHurtByMob(data.owner);
            }
        }
    }

    public static void clearAll() {
        activeFields.clear();
    }

    private static class FieldData {
        final ServerLevel level;
        final Player owner;
        int damageTicks;

        FieldData(ServerLevel level, Player owner) {
            this.level = level;
            this.owner = owner;
        }
    }
}
