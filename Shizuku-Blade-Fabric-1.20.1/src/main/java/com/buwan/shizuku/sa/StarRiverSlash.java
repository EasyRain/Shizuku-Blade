package com.buwan.shizuku.sa;

import com.buwan.shizuku.shizuku;
import mods.flammpfeil.slashblade.init.SBEntityTypes;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 星河流星斩辅助（3.6.0）：宇宙刀待机连段的命令路由 + 幻影剑气。
 */
public class StarRiverSlash {
    private static final int STAR_COLOR = 9662683;
    private static final float[] PHANTOM_ROLLS = new float[]{0.0F, 90.0F, 60.0F, -60.0F};

    private StarRiverSlash() {
    }

    public static void firePhantomSlash(LivingEntity entity) {
        if (!entity.level().isClientSide()) {
            if (entity instanceof Player player) {
                ServerLevel level = (ServerLevel) entity.level();
                int colorCode = CapabilitySlashBlade.BLADESTATE.maybeGet(player.getMainHandItem())
                        .map(ISlashBladeState::getColorCode).orElse(STAR_COLOR);
                float roll = randomPhantomRoll(player.getRandom());
                // 3.7.0：幻影剑气用穿透音效（三叉戟投掷声），伤害固定 0.7
                AttackManager.playPiercingSoundAction(player);
                spawnDrive(player, level, roll, 0.4F, colorCode, 0.7F, 1.0F, 16, 1.0F, KnockBacks.cancel);
            }
        }
    }

    public static float randomPhantomRoll(RandomSource rand) {
        return PHANTOM_ROLLS[rand.nextInt(PHANTOM_ROLLS.length)];
    }

    public static ResourceLocation standbyCommand(LivingEntity user) {
        IInputStateView view = new IInputStateView(user);
        EnumSet<InputCommand> commands = view.getCommands();
        if (commands.contains(InputCommand.R_CLICK)) {
            // 右键：星河连段（幻影剑气）
            return shizuku.prefix("cosmic_attack");
        }
        // 3.7.0：左键走原版默认连段第一段（combo_a1），恢复出刀动画与伤害
        return commands.contains(InputCommand.L_CLICK) ? SlashBlade.prefix("combo_a1") : SlashBlade.prefix("none");
    }

    /** 1.21.1 用 AttachmentType 获取输入状态的小包装（可空安全）。 */
    private static final class IInputStateView {
        private final LivingEntity owner;

        IInputStateView(LivingEntity owner) {
            this.owner = owner;
        }

        EnumSet<InputCommand> getCommands() {
            var state = CapabilityInputState.INPUT_STATE.maybeGet(owner).orElse(null);
            return state != null ? state.getCommands(owner) : EnumSet.noneOf(InputCommand.class);
        }
    }

    private static void spawnDrive(Player player, ServerLevel level, float roll, float up, int colorCode,
                                   float damage, float size, int lifetime, float speed, KnockBacks knockback) {
        Vec3 dir = player.getViewVector(1.0F);
        Vec3 pos = player.position().add(0.0, player.getBbHeight() * 0.75 + up, 0.0)
                .add(player.getViewVector(1.0F).scale(0.3));
        EntityDrive drive = new EntityDrive(SBEntityTypes.DRIVE, level);
        drive.setPos(pos.x, pos.y, pos.z);
        drive.setDamage(damage);
        drive.setSpeed(speed);
        drive.shoot(dir.x, dir.y, dir.z, speed, 0.0F);
        drive.setOwner(player);
        drive.setRotationRoll(roll);
        drive.color(colorCode);
        drive.setIsCritical(false);
        drive.setKnockBack(knockback);
        drive.setLifetime(lifetime);
        drive.setBaseSize(size);
        CapabilityConcentrationRank.RANK_POINT.maybeGet(player).ifPresent(rank ->
                drive.setRank(rank.getRankLevel(level.getGameTime())));
        level.addFreshEntity(drive);
    }
}
