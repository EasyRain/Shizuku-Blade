package com.buwan.shizuku.sa;

import com.buwan.shizuku.util.DamageCalculator;
import mods.flammpfeil.slashblade.init.SBEntityTypes;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 星河斩（galaxy_arc）：巨型刀光 + 六连剑气（3.6.0 移植）。
 */
public class GalaxyArc {
    private static final int STAR_COLOR = 9662683;
    private static final float[] QI_ROLLS = new float[]{60.0F, -60.0F, 90.0F, 60.0F, -60.0F, 90.0F};

    private GalaxyArc() {
    }

    public static void fireGiantDaoguang(LivingEntity entity) {
        if (!entity.level().isClientSide()) {
            if (entity instanceof Player player) {
                ServerLevel level = (ServerLevel) entity.level();
                int color = CapabilitySlashBlade.getBladeState(player.getMainHandItem())
                        .map(ISlashBladeState::getColorCode).orElse(STAR_COLOR);
                Vec3 dir = player.getViewVector(1.0F);
                Vec3 pos = player.position().add(0.0, player.getBbHeight() * 0.75 + 0.4, 0.0)
                        .add(player.getViewVector(1.0F).scale(0.3));
                EntityDrive drive = new EntityDrive(SBEntityTypes.DRIVE, level);
                drive.setPos(pos.x, pos.y, pos.z);
                // 3.7.0：巨大刀光伤害固定 3.0（不再随面板缩放）
                drive.setDamage(3.0F);
                drive.setSpeed(1.0F);
                drive.shoot(dir.x, dir.y, dir.z, 1.0F, 0.0F);
                drive.setOwner(player);
                drive.setRotationRoll(0.0F);
                drive.setColor(color);
                drive.setIsCritical(true);
                drive.setKnockBack(KnockBacks.cancel);
                drive.setLifetime(20.0F);
                drive.setBaseSize(3.0F);
                var rank = player.getData(CapabilityConcentrationRank.RANK_POINT.get());
                if (rank != null) {
                    drive.setRank(rank.getRankLevel(level.getGameTime()));
                }
                level.addFreshEntity(drive);
            }
        }
    }

    public static void fireQiSlash(LivingEntity entity, int index) {
        if (!entity.level().isClientSide()) {
            if (entity instanceof Player player) {
                if (index >= 0 && index < QI_ROLLS.length) {
                    ServerLevel level = (ServerLevel) entity.level();
                    int color = CapabilitySlashBlade.getBladeState(player.getMainHandItem())
                            .map(ISlashBladeState::getColorCode).orElse(STAR_COLOR);
                    float damage = DamageCalculator.calculateRefinedDamage(player, 1.0F) * 1.5F;
                    Vec3 pos = player.position().add(0.0, player.getBbHeight() * 0.75 + 0.3, 0.0)
                            .add(player.getViewVector(1.0F).scale(1.1F));
                    spawnSlashEffect(player, level, QI_ROLLS[index], pos, color, 0.0F, 2.2F, 20,
                            index == 5, KnockBacks.smash);
                    applyQiDamage(player, level, pos, damage);
                }
            }
        }
    }

    private static void spawnSlashEffect(Player player, ServerLevel level, float roll, Vec3 pos, int color,
                                         float damage, float size, int lifetime, boolean crit, KnockBacks knockback) {
        ItemStack blade = player.getMainHandItem();
        CapabilitySlashBlade.getBladeState(blade).ifPresent(state -> {
            SlashBladeEvent.DoSlashEvent event = new SlashBladeEvent.DoSlashEvent(
                    blade, state, player, roll, crit, damage, knockback);
            SlashBladeEvent.DO_SLASH.invoker().onDoSlash(event);
            if (!event.isCanceled()) {
                EntitySlashEffect se = new EntitySlashEffect(SBEntityTypes.SLASH_EFFECT, level);
                se.setPos(pos.x, pos.y, pos.z);
                se.setOwner(event.getUser());
                se.setRotationRoll(event.getRoll());
                se.setYRot(player.getYRot());
                se.setXRot(0.0F);
                se.setColor(color);
                se.setMute(true);
                se.setIsCritical(event.isCritical());
                se.setDamage(event.getDamage());
                se.setKnockBack(event.getKnockback());
                se.setBaseSize(size);
                se.setLifetime(lifetime);
                var rank = player.getData(CapabilityConcentrationRank.RANK_POINT.get());
                if (rank != null) {
                    se.setRank(rank.getRankLevel(level.getGameTime()));
                }
                level.addFreshEntity(se);
            }
        });
    }

    private static void applyQiDamage(Player player, ServerLevel level, Vec3 center, float damage) {
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(center.x - 6.0, center.y - 3.0, center.z - 6.0, center.x + 6.0, center.y + 3.0, center.z + 6.0),
                e -> e != player && !(e instanceof Player) && e.isAlive() && !e.isRemoved()
                        && e.distanceToSqr(center) <= 36.0
        );
        if (!targets.isEmpty()) {
            for (LivingEntity target : targets) {
                target.invulnerableTime = 0;
                // 3.7.0：AoE 伤害源改为间接魔法伤害（归属玩家），与 1.20.1 一致
                target.hurt(player.damageSources().indirectMagic(null, player), damage);
            }
        }
    }
}
