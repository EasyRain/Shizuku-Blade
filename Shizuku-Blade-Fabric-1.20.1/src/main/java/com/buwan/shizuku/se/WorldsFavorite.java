package com.buwan.shizuku.se;

import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 世界的偏爱者（SE 0）：持有该 SE 的玩家免疫伤害。
 * 客户端坐标位移逻辑见 {@link WorldsFavoriteClient}。
 */
public class WorldsFavorite extends SpecialEffect {

    /** 由客户端维护的坐标位移模式（UUID → 是否开启）。 */
    public static final Map<UUID, Boolean> coordinateShiftMode = new HashMap<>();
    public static final double SHIFT_SPEED = 0.5;

    public WorldsFavorite() {
        super(0);
    }

    public static boolean hasWorldsFavoriteStatic(LivingEntity entity) {
        ItemStack mainHand = entity.getMainHandItem();
        if (!mainHand.isEmpty() && isBladeWithFavor(mainHand, entity)) {
            return true;
        }
        ItemStack offHand = entity.getOffhandItem();
        return !offHand.isEmpty() && isBladeWithFavor(offHand, entity);
    }

    private static boolean isBladeWithFavor(ItemStack stack, LivingEntity entity) {
        return CapabilitySlashBlade.BLADESTATE.maybeGet(stack).map(state ->
                state.hasSpecialEffect(SERegistry.WORLDS_FAVORITE.getId())
                        && SpecialEffect.isEffective(SERegistry.WORLDS_FAVORITE.getId(),
                        entity instanceof Player p ? p.experienceLevel : 0)).orElse(false);
    }

    /** 由 shizuku.init() 调用。 */
    public static void registerCommon() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (hasWorldsFavoriteStatic(entity)) {
                entity.setHealth(entity.getMaxHealth());
                return false;
            }
            return true;
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                coordinateShiftMode.remove(handler.getPlayer().getUUID()));
    }
}
