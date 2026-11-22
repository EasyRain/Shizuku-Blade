package com.buwan.shizuku.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;

/**
 * 血量修改的门面（对应 core/HealthModify 的 util 版）：四种策略按顺序尝试，
 * 并额外提供 traversalModify 沿「主人/召唤者」链找真正的血量载体。
 */
public class HealthModifyHelper {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("shizuku/healthmodifyhelper");
    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("78364952-8455-5678-9abc-785463259abc");

    public static boolean entityDataModify(Entity target, float newValue) {
        if (target.isRemoved() && !target.isAddedToWorld()) {
            return false;
        } else if (target instanceof LivingEntity le) {
            if (le.getHealth() < 0.0F) {
                return false;
            } else {
                try {
                    SynchedEntityData entityData = target.getEntityData();
                    Field itemsByIdField = getDeclaredFieldRecursive(SynchedEntityData.class, "f_135345_");
                    itemsByIdField.setAccessible(true);
                    Map<Integer, ?> itemsById = (Map<Integer, ?>)itemsByIdField.get(entityData);
                    Field dataHealthIdField = getDeclaredFieldRecursive(LivingEntity.class, "f_20961_");
                    dataHealthIdField.setAccessible(true);
                    Object dataHealthId = dataHealthIdField.get(null);

                    for (Entry<Integer, ?> entry : itemsById.entrySet()) {
                        Object dataItem = entry.getValue();
                        Field valueField = getDeclaredFieldRecursive(dataItem.getClass(), "f_135391_");
                        valueField.setAccessible(true);
                        Object value = valueField.get(dataItem);
                        if (value instanceof Float fv && fv == le.getHealth()) {
                            valueField.set(dataItem, newValue);
                        }

                        if (value instanceof Double dv && dv == le.getHealth()) {
                            valueField.set(dataItem, (double)newValue);
                        }

                        if (value instanceof Integer iv && iv.intValue() == le.getHealth()) {
                            valueField.set(dataItem, (int)newValue);
                        }

                        try {
                            Method setMethod = SynchedEntityData.class.getDeclaredMethod("m_135381_", Object.class, Object.class);
                            setMethod.setAccessible(true);
                            setMethod.invoke(entityData, dataHealthId, newValue);
                        } catch (NoSuchMethodException var16) {
                            try {
                                Method setMethodx = SynchedEntityData.class.getDeclaredMethod("set", Object.class, Object.class);
                                setMethodx.setAccessible(true);
                                setMethodx.invoke(entityData, dataHealthId, newValue);
                            } catch (NoSuchMethodException var15) {
                            }
                        }
                    }

                    return true;
                } catch (Throwable var17) {
                    System.err.println("[HealthModifyHelper] entityDataModify \u5f02\u5e38: " + var17.getMessage());
                    LOGGER.debug("unexpected error", var17);
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    public static boolean traversalModify(Entity entity, Entity player, float newValue, boolean look) {
        if (entity instanceof LivingEntity le) {
            if (le instanceof Player) {
                return false;
            } else {
                le.setHealth(newValue);
                return true;
            }
        } else {
            return false;
        }
    }

    public static boolean traversalModify(Entity entity, Entity player, float newValue) {
        traversalModify(entity, player, newValue, true);
        return true;
    }

    public static boolean forceModify(Entity entity, float newValue, float newMaxValue) {
        if (entity instanceof LivingEntity le) {
            le.setHealth(newValue);
        }

        return true;
    }

    public static boolean tagModify(Entity entity, float newValue) {
        if (entity instanceof LivingEntity le) {
            if (le.getHealth() < 0.0F) {
                return false;
            }

            CompoundTag ct = new CompoundTag();
            entity.saveWithoutId(ct);
            ct.putFloat("Health", newValue);
            entity.load(ct);
            if (!entity.level().isClientSide) {
                if (entity.level().isClientSide || !(entity instanceof ServerPlayer serverPlayer)) {
                    return false;
                }

                serverPlayer.connection
                    .send(new ClientboundSetHealthPacket(newValue, serverPlayer.getFoodData().getFoodLevel(), serverPlayer.getFoodData().getSaturationLevel()));
            }
        }

        return true;
    }

    public static boolean attributeModifier(Entity entity, float newMaxValue) {
        if (entity instanceof LivingEntity le) {
            if (le.getHealth() < 0.0F) {
                return false;
            } else {
                AttributeInstance maxHealthAttribute = le.getAttribute(Attributes.MAX_HEALTH);
                if (maxHealthAttribute == null) {
                    return false;
                } else {
                    maxHealthAttribute.removeModifier(HEALTH_MODIFIER_UUID);
                    double currentMax = maxHealthAttribute.getBaseValue();
                    double difference = newMaxValue - currentMax;
                    if (Math.abs(difference) > 0.001) {
                        AttributeModifier modifier = new AttributeModifier(HEALTH_MODIFIER_UUID, "health_modifier", difference, Operation.ADDITION);
                        maxHealthAttribute.addPermanentModifier(modifier);
                    }

                    return true;
                }
            }
        } else {
            return false;
        }
    }

    private static Field getDeclaredFieldRecursive(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        for (Class<?> currentClass = clazz; currentClass != null; currentClass = currentClass.getSuperclass()) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException var4) {
            }
        }

        throw new NoSuchFieldException(fieldName);
    }

    private static class HealthMethodHelper {
        public static void forceSetHealth(Entity entity, float health, float maxHealth) {
            if (entity instanceof LivingEntity le) {
                try {
                    le.setHealth(health);
                    AttributeInstance maxHealthAttr = le.getAttribute(Attributes.MAX_HEALTH);
                    if (maxHealthAttr != null) {
                        maxHealthAttr.setBaseValue(maxHealth);
                    }

                    try {
                        Field healthField = LivingEntity.class.getDeclaredField("f_19840_");
                        healthField.setAccessible(true);
                        healthField.setFloat(le, health);
                    } catch (Exception var7) {
                    }

                    try {
                        Field healthValueField = LivingEntity.class.getDeclaredField("f_20898_");
                        healthValueField.setAccessible(true);
                        healthValueField.setFloat(le, health);
                    } catch (Exception var6) {
                    }
                } catch (Exception var8) {
                    System.err.println("[HealthModifyHelper] forceSetHealth \u5f02\u5e38: " + var8.getMessage());
                }
            }
        }
    }

    private static class Traversal {
        public static void modifyHealthStable(LivingEntity le, Player p, float currentValue, float newValue, boolean look) {
            try {
                Field healthField = LivingEntity.class.getDeclaredField("f_19840_");
                healthField.setAccessible(true);
                healthField.setFloat(le, newValue);
                if (!le.level().isClientSide && le instanceof ServerPlayer serverPlayer) {
                    serverPlayer.connection
                        .send(
                            new ClientboundSetHealthPacket(newValue, serverPlayer.getFoodData().getFoodLevel(), serverPlayer.getFoodData().getSaturationLevel())
                        );
                }
            } catch (Exception var7) {
                System.err.println("[HealthModifyHelper] modifyHealthStable \u5f02\u5e38: " + var7.getMessage());
            }
        }
    }
}
