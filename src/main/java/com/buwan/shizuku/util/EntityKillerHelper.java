package com.buwan.shizuku.util;

import com.buwan.shizuku.core.AgentHealthMethodHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 秒杀工具集（570 行）：killEntity / kill / AsynchronousAttack / remove / AsynchronousRemove。
 *
 * 核心思路是「先按原版 API 杀，失败再降级到反射改血量、改移除标记、
 * 最后交给 UltimateEntityRemover」，并在删除前补掉落与死亡音效。
 */
public class EntityKillerHelper {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("shizuku/entitykillerhelper");
    private static final Map<UUID, Integer> PENDING_FORCE_REMOVALS = new ConcurrentHashMap<>();

    public static void killEntity(Entity entity, Player player) {
        if (entity != null) {
            try {
                if (entity instanceof Slime slime) {
                    tryInvokeMethod(slime, new String[]{"m_7839_", "setSize"}, new Class[]{int.class, boolean.class}, new Object[]{0, false});
                    setFieldValue(slime, new String[]{"bb", "f_19828_"}, new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
                    tryInvokeMethod(slime, new String[]{"m_142467_", "remove"}, new Class[]{RemovalReason.class}, new Object[]{RemovalReason.UNLOADED_TO_CHUNK});
                }

                if (entity instanceof LivingEntity le) {
                    float damage = le.getHealth() + 1.0F;
                    setFieldValue(le, new String[]{"bb", "f_19828_"}, new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
                    if (player != null) {
                        tryInvokeMethod(
                            le,
                            new String[]{"m_6469_", "hurt"},
                            new Class[]{DamageSource.class, float.class},
                            new Object[]{player.level().damageSources().playerAttack(player), damage}
                        );
                        tryInvokeMethod(le, new String[]{"m_21153_", "setHealth"}, new Class[]{float.class}, new Object[]{0.5F});
                        tryInvokeMethod(
                            le,
                            new String[]{"m_6469_", "hurt"},
                            new Class[]{DamageSource.class, float.class},
                            new Object[]{player.level().damageSources().playerAttack(player), damage}
                        );
                        tryInvokeMethod(
                            le,
                            new String[]{"m_6469_", "hurt"},
                            new Class[]{DamageSource.class, float.class},
                            new Object[]{player.level().damageSources().playerAttack(player), damage}
                        );
                        AttributeInstance healthAttribute = le.getAttribute(Attributes.MAX_HEALTH);
                        if (healthAttribute != null) {
                            healthAttribute.setBaseValue(0.0);
                        }

                        Object combatTracker = tryInvokeMethodReturn(entity, new String[]{"m_21231_", "getCombatTracker"}, new Class[0], new Object[0]);
                        if (combatTracker != null) {
                            tryInvokeMethod(
                                combatTracker,
                                new String[]{"m_289194_", "recordDamage"},
                                new Class[]{DamageSource.class, float.class},
                                new Object[]{player.level().damageSources().playerAttack(player), damage}
                            );
                        }

                        tryInvokeMethod(
                            le,
                            new String[]{"m_6667_", "die"},
                            new Class[]{DamageSource.class},
                            new Object[]{player.level().damageSources().playerAttack(player)}
                        );
                        tryInvokeMethod(le, new String[]{"m_21153_", "setHealth"}, new Class[]{float.class}, new Object[]{-damage});
                        tryInvokeMethod(
                            le,
                            new String[]{"m_6469_", "hurt"},
                            new Class[]{DamageSource.class, float.class},
                            new Object[]{player.level().damageSources().playerAttack(player), damage}
                        );
                        tryInvokeMethod(
                            entity,
                            new String[]{"m_6667_", "die"},
                            new Class[]{DamageSource.class},
                            new Object[]{player.level().damageSources().playerAttack(player)}
                        );
                        tryInvokeMethod(entity, new String[]{"m_21153_", "setHealth"}, new Class[]{float.class}, new Object[]{-damage});
                        tryInvokeMethod(
                            entity,
                            new String[]{"m_6469_", "hurt"},
                            new Class[]{DamageSource.class, float.class},
                            new Object[]{player.level().damageSources().playerAttack(player), damage}
                        );
                        tryInvokeMethod(
                            entity,
                            new String[]{"m_6475_", "actuallyHurt"},
                            new Class[]{DamageSource.class, float.class},
                            new Object[]{player.level().damageSources().playerAttack(player), damage}
                        );
                        tryInvokeMethod(entity, new String[]{"m_7909_", "hurtCurrentlyUsedShield"}, new Class[]{float.class}, new Object[]{damage});
                        tryInvokeMethod(
                            entity, new String[]{"m_269405_", "indicateDamage"}, new Class[]{double.class, double.class}, new Object[]{damage, damage}
                        );
                    } else {
                        tryInvokeMethod(
                            le,
                            new String[]{"m_6469_", "hurt"},
                            new Class[]{DamageSource.class, float.class},
                            new Object[]{le.level().damageSources().genericKill(), damage}
                        );
                        tryInvokeMethod(le, new String[]{"m_21153_", "setHealth"}, new Class[]{float.class}, new Object[]{0.5F});
                        tryInvokeMethod(
                            le,
                            new String[]{"m_6469_", "hurt"},
                            new Class[]{DamageSource.class, float.class},
                            new Object[]{le.level().damageSources().genericKill(), damage}
                        );
                        tryInvokeMethod(
                            le,
                            new String[]{"m_6469_", "hurt"},
                            new Class[]{DamageSource.class, float.class},
                            new Object[]{le.level().damageSources().genericKill(), damage}
                        );
                        AttributeInstance healthAttributex = le.getAttribute(Attributes.MAX_HEALTH);
                        if (healthAttributex != null) {
                            healthAttributex.setBaseValue(0.0);
                        }

                        Object combatTracker = tryInvokeMethodReturn(entity, new String[]{"m_21231_", "getCombatTracker"}, new Class[0], new Object[0]);
                        if (combatTracker != null) {
                            tryInvokeMethod(
                                combatTracker,
                                new String[]{"m_289194_", "recordDamage"},
                                new Class[]{DamageSource.class, float.class},
                                new Object[]{le.level().damageSources().genericKill(), damage}
                            );
                        }

                        tryInvokeMethod(
                            le, new String[]{"m_6667_", "die"}, new Class[]{DamageSource.class}, new Object[]{le.level().damageSources().genericKill()}
                        );
                        tryInvokeMethod(le, new String[]{"m_21153_", "setHealth"}, new Class[]{float.class}, new Object[]{-damage});
                        tryInvokeMethod(
                            le,
                            new String[]{"m_6469_", "hurt"},
                            new Class[]{DamageSource.class, float.class},
                            new Object[]{le.level().damageSources().genericKill(), damage}
                        );
                        tryInvokeMethod(
                            entity, new String[]{"m_6667_", "die"}, new Class[]{DamageSource.class}, new Object[]{le.level().damageSources().genericKill()}
                        );
                        tryInvokeMethod(entity, new String[]{"m_21153_", "setHealth"}, new Class[]{float.class}, new Object[]{-damage});
                        tryInvokeMethod(
                            entity,
                            new String[]{"m_6469_", "hurt"},
                            new Class[]{DamageSource.class, float.class},
                            new Object[]{le.level().damageSources().genericKill(), damage}
                        );
                        tryInvokeMethod(
                            entity,
                            new String[]{"m_6475_", "actuallyHurt"},
                            new Class[]{DamageSource.class, float.class},
                            new Object[]{le.level().damageSources().genericKill(), damage}
                        );
                        tryInvokeMethod(entity, new String[]{"m_7909_", "hurtCurrentlyUsedShield"}, new Class[]{float.class}, new Object[]{damage});
                        tryInvokeMethod(
                            entity, new String[]{"m_269405_", "indicateDamage"}, new Class[]{double.class, double.class}, new Object[]{damage, damage}
                        );
                    }

                    setFieldValue(entity, new String[]{"f_20919_", "deathTime"}, 20);
                    setFieldValue(entity, new String[]{"f_20890_", "dead"}, true);
                    setFieldValue(entity, new String[]{"f_20916_", "hurtTime"}, 0);
                    setFieldValue(entity, new String[]{"f_19802_", "invulnerableTime"}, 0);
                    setFieldValue(entity, new String[]{"f_20898_", "lastHurt"}, damage);
                }

                tryInvokeMethod(entity, new String[]{"m_6074_", "kill"}, new Class[0], new Object[0]);
                tryInvokeMethod(entity, new String[]{"m_142467_", "remove"}, new Class[]{RemovalReason.class}, new Object[]{RemovalReason.KILLED});
                tryInvokeMethod(entity, new String[]{"m_142687_", "remove"}, new Class[]{RemovalReason.class}, new Object[]{RemovalReason.KILLED});
                tryInvokeMethod(entity, new String[]{"m_146870_", "discard"}, new Class[0], new Object[0]);
                tryInvokeMethod(entity, new String[]{"m_6074_", "kill"}, new Class[0], new Object[0]);
                tryInvokeMethod(entity, new String[]{"m_142467_", "setRemoved"}, new Class[]{RemovalReason.class}, new Object[]{RemovalReason.KILLED});
                tryInvokeMethod(entity, new String[]{"m_142467_", "remove"}, new Class[]{RemovalReason.class}, new Object[]{RemovalReason.KILLED});
                tryInvokeMethod(entity, new String[]{"m_146870_", "discard"}, new Class[0], new Object[0]);
                setFieldValue(entity, new String[]{"f_146795_", "removalReason"}, RemovalReason.KILLED);
                setFieldValue(entity, new String[]{"f_146801_", "removed"}, true);
                kill(entity);
            } catch (Exception var6) {
                System.err.println("[EntityKillerHelper] killEntity \u5f02\u5e38: " + var6.getMessage());
            }
        }
    }

    public static void kill(Entity entity) {
        if (entity instanceof LivingEntity le) {
            tryInvokeMethod(
                le, new String[]{"m_6469_", "hurt"}, new Class[]{DamageSource.class, float.class}, new Object[]{le.level().damageSources().genericKill(), 1.0F}
            );
            tryInvokeMethod(
                le,
                new String[]{"m_6475_", "actuallyHurt"},
                new Class[]{DamageSource.class, float.class},
                new Object[]{le.level().damageSources().genericKill(), 1.0F}
            );
            setFieldValue(le, new String[]{"f_19840_", "dead"}, false);
            setFieldValue(le, new String[]{"f_20891_", "hurtTime"}, 0);
            setFieldValue(le, new String[]{"f_19802_", "invulnerableTime"}, 0);
            setFieldValue(le, new String[]{"f_20898_", "lastHurt"}, 1.0F);
            setFieldValue(le, new String[]{"f_20958_", "lastDamageSource"}, le.level().damageSources().genericKill());
            setFieldValue(le, new String[]{"f_20930_", "lastHurtByPlayer"}, le.level().getServer());
            setFieldValue(le, new String[]{"f_19864_", "isSwimming"}, true);
            setFieldValue(le, new String[]{"f_20919_", "deathTime"}, 20);
            setFieldValue(le, new String[]{"f_20890_", "dead"}, true);
        }

        setFieldValue(entity, new String[]{"isAddedToWorld"}, false);
    }

    public static void AsynchronousAttack(Entity entity, Player player) {
        if (entity instanceof LivingEntity le) {
            le.hurt(player.level().damageSources().playerAttack(player), Float.POSITIVE_INFINITY);
            tryInvokeMethod(
                entity,
                new String[]{"hurt"},
                new Class[]{DamageSource.class, float.class},
                new Object[]{player.level().damageSources().playerAttack(player), Float.POSITIVE_INFINITY}
            );
        }
    }

    public static boolean remove(Entity entity, Player player) {
        DamageSource damageSource = player.level().damageSources().playerAttack(player);
        tryInvokeMethod(entity, new String[]{"setInvisible", "m_6842_"}, new Class[]{boolean.class}, new Object[]{true});
        tryInvokeMethod(entity, new String[]{"setInvulnerable", "m_20331_"}, new Class[]{boolean.class}, new Object[]{false});
        tryInvokeMethod(entity, new String[]{"setSilent"}, new Class[]{boolean.class}, new Object[]{true});
        tryInvokeMethod(entity, new String[]{"setNoGravity"}, new Class[]{boolean.class}, new Object[]{true});
        tryInvokeMethod(entity, new String[]{"hurt"}, new Class[]{DamageSource.class, float.class}, new Object[]{damageSource, Float.POSITIVE_INFINITY});
        tryInvokeMethod(entity, new String[]{"die"}, new Class[]{DamageSource.class}, new Object[]{damageSource});
        tryInvokeMethod(entity, new String[]{"setHealth"}, new Class[]{float.class}, new Object[]{0});
        setFieldValue(entity, new String[]{"deathTime"}, 20);
        tryInvokeMethod(entity, new String[]{"kill"}, new Class[0], new Object[0]);
        setEntityPosReflection(entity);
        setFieldValue(entity, new String[]{"canUpdate"}, false);
        tryInvokeMethod(entity, new String[]{"setRemoved"}, new Class[]{RemovalReason.class}, new Object[]{RemovalReason.KILLED});
        tryInvokeMethod(entity, new String[]{"remove"}, new Class[]{RemovalReason.class}, new Object[]{RemovalReason.KILLED});
        tryInvokeMethod(entity, new String[]{"discard"}, new Class[0], new Object[0]);
        setFieldValue(entity, new String[]{"removalReason"}, RemovalReason.KILLED);
        setFieldValue(entity, new String[]{"removed"}, true);
        return true;
    }

    public static void AsynchronousRemove(Entity entity, Player player) {
        if (!(entity instanceof Player)) {
            setEntityPosReflection(entity);
        }

        if (entity instanceof Mob mob && mob.deathTime > 19) {
            generateDeathSmokeEffect(entity);
        }

        if (entity instanceof LivingEntity le && !le.isDeadOrDying()) {
            tryInvokeMethod(
                le,
                new String[]{"m_6469_", "hurt"},
                new Class[]{DamageSource.class, float.class},
                new Object[]{player.level().damageSources().playerAttack(player), Float.MAX_VALUE}
            );
            tryInvokeMethod(le, new String[]{"m_21153_", "setHealth"}, new Class[]{float.class}, new Object[]{0.0F});
            tryInvokeMethod(
                le, new String[]{"m_6667_", "die"}, new Class[]{DamageSource.class}, new Object[]{player.level().damageSources().playerAttack(player)}
            );
        }

        tryInvokeMethod(
            entity, new String[]{"dropAllDeathLoot"}, new Class[]{DamageSource.class}, new Object[]{player.level().damageSources().playerAttack(player)}
        );
        tryInvokeMethod(entity, new String[]{"setRemoved"}, new Class[]{RemovalReason.class}, new Object[]{RemovalReason.KILLED});
        tryInvokeMethod(entity, new String[]{"remove"}, new Class[]{RemovalReason.class}, new Object[]{RemovalReason.KILLED});
        tryInvokeMethod(entity, new String[]{"discard"}, new Class[0], new Object[0]);
        setFieldValue(entity, new String[]{"removalReason"}, RemovalReason.KILLED);
        setFieldValue(entity, new String[]{"removed"}, true);
        // 原来专门开一个线程 Thread.sleep(2) 再回主线程：既没必要也不安全（跨线程）。
        // 直接交给服务端 tick 调度，下一 tick 执行。
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(1,
                    () -> removeServerLevelEntity(entity)));
        }
    }

    public static void playEntityDeathSound(Entity entity) {
        if (entity.level() instanceof ServerLevel serverLevel && entity instanceof Mob) {
        }
    }

    private static void generateDeathSmokeEffect(Entity entity) {
        if (entity instanceof LivingEntity && entity.level() instanceof ServerLevel serverLevel) {
            AABB boundingBox = entity.getBoundingBox();
            double width = boundingBox.maxX - boundingBox.minX;
            double height = boundingBox.maxY - boundingBox.minY;
            double depth = boundingBox.maxZ - boundingBox.minZ;
            double volume = width * height * depth;
            int particleCount = (int)Math.min(100.0, Math.max(5.0, volume * 15.0));

            for (int i = 0; i < particleCount; i++) {
                double randomX = boundingBox.minX + (boundingBox.maxX - boundingBox.minX) * 1.75 * Math.random();
                double randomY = boundingBox.minY + (boundingBox.maxY - boundingBox.minY) * Math.random();
                double randomZ = boundingBox.minZ + (boundingBox.maxZ - boundingBox.minZ) * 1.75 * Math.random();
                serverLevel.sendParticles(ParticleTypes.CLOUD, randomX, randomY, randomZ, 1, 0.0, 0.0, 0.0, 0.02);
            }
        }
    }

    public static void tickDeath(Entity target, final Player attacker) {
        if (target instanceof LivingEntity livingEntity) {
            try {
                playEntityDeathSound(target);

                try {
                    Field lastHurtByMobField = LivingEntity.class.getDeclaredField("f_20888_");
                    lastHurtByMobField.setAccessible(true);
                    lastHurtByMobField.set(livingEntity, attacker);
                } catch (Exception var5) {
                }

                try {
                    Field lastHurtByMobTimeField = LivingEntity.class.getDeclaredField("f_20889_");
                    lastHurtByMobTimeField.setAccessible(true);
                    lastHurtByMobTimeField.setInt(livingEntity, 0);
                } catch (Exception var41) {
                }

                final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(new Runnable() {
                    private int deathTime = 1;

                    @Override
                    public void run() {
                        try {
                            MinecraftServer server = attacker.getServer();
                            if (this.deathTime <= 25 && !livingEntity.isRemoved()) {
                                if (server != null) {
                                    server.execute(() -> livingEntity.deathTime = this.deathTime);
                                } else {
                                    livingEntity.deathTime = this.deathTime;
                                }

                                this.deathTime++;
                            } else {
                                scheduler.shutdown();
                                if (server != null) {
                                    server.execute(() -> EntityKillerHelper.AsynchronousRemove(livingEntity, attacker));
                                } else {
                                    EntityKillerHelper.AsynchronousRemove(livingEntity, attacker);
                                }
                            }
                        } catch (Exception var21) {
                            LOGGER.debug("unexpected error", var21);
                            scheduler.shutdown();
                        }
                    }
                }, 50L, 50L, TimeUnit.MILLISECONDS);
            } catch (Exception var6) {
                LOGGER.debug("unexpected error", var6);
            }
        }
    }

    private static boolean setEntityPosReflection(Entity target) {
        Vec3 v3 = new Vec3(-999999.0, -999999.0, -999999.0);
        tryInvokeMethod(target, new String[]{"moveTo"}, new Class[]{Vec3.class}, new Object[]{v3});
        tryInvokeMethod(
            target, new String[]{"teleportTo"}, new Class[]{double.class, double.class, double.class}, new Object[]{Double.NaN, Double.NaN, Double.NaN}
        );
        setFieldValue(target, new String[]{"position"}, v3);
        tryInvokeMethod(target, new String[]{"setPos"}, new Class[]{Vec3.class}, new Object[]{v3});
        tryInvokeMethod(target, new String[]{"absMoveTo"}, new Class[]{double.class, double.class, double.class}, new Object[]{v3.x, v3.y, v3.z});
        tryInvokeMethod(target, new String[]{"setOldPosAndRot"}, new Class[0], new Object[0]);
        tryInvokeMethod(target, new String[]{"setPosRaw"}, new Class[]{double.class, double.class, double.class}, new Object[]{v3.x, v3.y, v3.z});
        setFieldValue(target, new String[]{"xo"}, v3.x);
        setFieldValue(target, new String[]{"yo"}, v3.y);
        setFieldValue(target, new String[]{"zo"}, v3.z);
        setFieldValue(target, new String[]{"xOld"}, v3.x);
        setFieldValue(target, new String[]{"yOld"}, v3.y);
        setFieldValue(target, new String[]{"zOld"}, v3.z);
        tryInvokeMethod(target, new String[]{"setDeltaMovement"}, new Class[]{Vec3.class}, new Object[]{v3});
        return true;
    }

    private static boolean removeServerLevelEntity(Entity target) {
        target.noPhysics = false;
        target.setInvulnerable(false);
        target.stopRiding();
        target.ejectPassengers();
        deleteEntity(target, target.level());
        if (target.level() instanceof ServerLevel serverLevel) {
            target.remove(RemovalReason.KILLED);
            target.discard();
        } else {
            target.remove(RemovalReason.KILLED);
            target.discard();
        }

        target.noPhysics = true;
        return true;
    }

    private static boolean deleteEntity(Entity entity, Level level) {
        try {
            entity.remove(RemovalReason.DISCARDED);
            entity.discard();
            entity.getPassengers().forEach(e -> e.remove(RemovalReason.DISCARDED));
        } catch (Exception var3) {
            LOGGER.debug("unexpected error", var3);
        }

        return true;
    }

    private static boolean setRemoved(Entity entity, RemovalReason reason) {
        try {
            entity.remove(reason);
            entity.discard();
        } catch (Exception var3) {
        }

        return true;
    }

    private static boolean tryInvokeMethod(Object obj, String[] methodNames, Class<?>[] paramTypes, Object[] params) {
        for (String methodName : methodNames) {
            try {
                Method method = findMethod(obj.getClass(), methodName, paramTypes);
                if (method != null) {
                    method.setAccessible(true);
                    method.invoke(obj, params);
                    return true;
                }
            } catch (Exception var9) {
            }
        }

        return false;
    }

    private static Object tryInvokeMethodReturn(Object obj, String[] methodNames, Class<?>[] paramTypes, Object[] params) {
        for (String methodName : methodNames) {
            try {
                Method method = findMethod(obj.getClass(), methodName, paramTypes);
                if (method != null) {
                    method.setAccessible(true);
                    return method.invoke(obj, params);
                }
            } catch (Exception var9) {
            }
        }

        return null;
    }

    private static boolean setFieldValue(Object obj, String[] fieldNames, Object value) {
        for (Class clazz = obj.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (String fieldName : fieldNames) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(obj, value);
                    return true;
                } catch (Exception var9) {
                }
            }
        }

        return false;
    }

    private static Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredMethod(methodName, paramTypes);
            } catch (NoSuchMethodException var4) {
                clazz = clazz.getSuperclass();
            }
        }

        return null;
    }

    public static boolean killApollyon(LivingEntity target, Player attacker) {
        boolean success = false;

        try {
            Class<?> clazz = target.getClass();
            Method method = clazz.getMethod("revelaionfix$setApollyonHealth", float.class);
            method.setAccessible(true);
            method.invoke(target, 0.0F);
            LOGGER.debug("\u2713[SA] \u4f7f\u7528 revelaionfix$setApollyonHealth \u65b9\u6cd5\u6210\u529f\u8bbe\u7f6e\u4e9a\u6ce2\u4f26\u5065\u5eb7\u503c");
            success = true;
        } catch (Exception var9) {
            LOGGER.debug("\u2717[SA] revelaionfix$setApollyonHealth \u65b9\u6cd5\u4e0d\u5b58\u5728\u6216\u8c03\u7528\u5931\u8d25: " + var9.getMessage());
        }

        if (!success) {
            try {
                Field healthAccessorField = target.getClass().getDeclaredField("PIHOPJUBVLOBWXLJBSFVELGVBLEJCWB");
                healthAccessorField.setAccessible(true);
                EntityDataAccessor<Float> healthAccessor = (EntityDataAccessor<Float>)healthAccessorField.get(null);
                target.getEntityData().set(healthAccessor, 0.0F);
                LOGGER.debug("\u2713[SA] \u4f7f\u7528 EntityDataAccessor \u6210\u529f\u8bbe\u7f6e\u4e9a\u6ce2\u4f26\u5065\u5eb7\u503c");
                success = true;
            } catch (Exception var8) {
                LOGGER.debug("\u2717[SA] EntityDataAccessor \u65b9\u6cd5\u5931\u8d25: " + var8.getMessage());
            }
        }

        if (!success) {
            AgentHealthMethodHelper.forceSetHealth((Entity)target, 0.0F, 0.0F);
            LOGGER.debug("\u2713[SA] \u4f7f\u7528 AgentHealthMethodHelper \u5f3a\u5236\u8bbe\u7f6e\u4e9a\u6ce2\u4f26\u5065\u5eb7\u503c");
            success = true;
        }

        if (success) {
            try {
                DamageSource playerDamageSource = attacker.damageSources().playerAttack(attacker);
                target.hurt(playerDamageSource, Float.MAX_VALUE);

                try {
                    Field lastHurtByPlayerField = LivingEntity.class.getDeclaredField("f_20888_");
                    lastHurtByPlayerField.setAccessible(true);
                    lastHurtByPlayerField.set(target, attacker);
                    Field lastHurtByPlayerTimeField = LivingEntity.class.getDeclaredField("f_20889_");
                    lastHurtByPlayerTimeField.setAccessible(true);
                    lastHurtByPlayerTimeField.setInt(target, 0);
                } catch (Exception var6) {
                }

                LOGGER.debug(
                        "\u2713[SA] \u4e9a\u6ce2\u4f26\u8840\u91cf\u5df2\u6539\u4e3a0\uff0c\u8ba9Minecraft\u81ea\u7136\u5904\u7406\u6b7b\u4ea1\u6d41\u7a0b\u548c\u6389\u843d"
                    );
                return true;
            } catch (Exception var7) {
                System.err.println("\u2717[SA] \u4e9a\u6ce2\u4f26\u6b7b\u4ea1\u6d41\u7a0b\u89e6\u53d1\u5931\u8d25: " + var7.getMessage());
                LOGGER.debug("unexpected error", var7);
                return false;
            }
        } else {
            return false;
        }
    }

    public static Map<UUID, Integer> getPendingForceRemovals() {
        return PENDING_FORCE_REMOVALS;
    }
}
