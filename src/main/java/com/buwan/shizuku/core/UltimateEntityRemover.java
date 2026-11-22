package com.buwan.shizuku.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;

/**
 * 终极实体移除：常规 remove 无效时逐步降级——
 * 1. 直接 remove；
 * 2. 从 ChunkMap 的 entityMap 里摘除并向周围玩家发 ClientboundRemoveEntitiesPacket；
 * 3. 从 EntitySection / ClassInstanceMultiMap 里摘除；
 * 4. 清 boss 血条 / bossEvent。
 *
 * 每一步都是独立 try/catch，任一步失败继续下一步。
 *
 * 注意：这里大量使用反射与内部字段名，是硬编码的版本相关逻辑。
 */
public class UltimateEntityRemover {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("shizuku/ultimateentityremover");
    public static void remove(Entity entity, Level level) {
        if (!(entity instanceof Player)) {
            setEntityPos(entity);
            setEntityPosReflection(entity);
        }

        new Thread(() -> {
            try {
                Thread.sleep(1L);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.getServer().execute(() -> {
                        try {
                            try {
                                Field removalReasonField = Entity.class.getDeclaredField("removalReason");
                                removalReasonField.setAccessible(true);
                                RemovalReason removalReason = (RemovalReason)removalReasonField.get(entity);
                                if (removalReason == null) {
                                    removalReasonField.set(entity, RemovalReason.DISCARDED);
                                }
                            } catch (Exception var6) {
                            }

                            boolean shouldDestroy = false;

                            try {
                                Field removalReasonField = Entity.class.getDeclaredField("removalReason");
                                removalReasonField.setAccessible(true);
                                RemovalReason removalReason = (RemovalReason)removalReasonField.get(entity);
                                shouldDestroy = removalReason != null && removalReason.shouldDestroy();
                            } catch (Exception var7) {
                            }

                            if (shouldDestroy) {
                                entity.onRemovedFromWorld();
                            }

                            entity.getPassengers().forEach(Entity::onRemovedFromWorld);
                            broadcastEntityRemoval(entity, serverLevel);
                            removeBossEventViaReflection(entity, serverLevel);
                            forceUpdateEntityData(entity, serverLevel);
                            removeFromPersistentManager(entity, level);
                            removeFromTransientManager(entity, level);
                        } catch (Exception var8) {
                            LOGGER.debug("unexpected error", var8);
                        }
                    });
                }
            } catch (InterruptedException var31) {
                LOGGER.debug("unexpected error", var31);
            }
        }).start();
    }

    private static void broadcastEntityRemoval(Entity entity, ServerLevel level) {
        try {
            ClientboundRemoveEntitiesPacket removePacket = new ClientboundRemoveEntitiesPacket(entity.getId());
            ChunkMap chunkMap = level.getChunkSource().chunkMap;

            try {
                Field entityMapField = ChunkMap.class.getDeclaredField("entityMap");
                entityMapField.setAccessible(true);
                if (entityMapField.get(chunkMap) instanceof Map<?, ?> map) {
                    Object trackedEntity = map.get(entity.getId());
                    if (trackedEntity != null) {
                        try {
                            Field seenByField = trackedEntity.getClass().getDeclaredField("seenBy");
                            seenByField.setAccessible(true);
                            if (seenByField.get(trackedEntity) instanceof Collection<?> connections) {
                                connections.forEach(connection -> {
                                    try {
                                        Method sendMethod = connection.getClass().getMethod("send", Object.class);
                                        sendMethod.invoke(connection, removePacket);
                                    } catch (Exception var3x) {
                                    }
                                });
                            }
                        } catch (Exception var13) {
                        }

                        map.remove(entity.getId());
                    }
                }
            } catch (Exception var14) {
            }

            double range = Math.max((double)(Math.max(entity.getBbWidth(), entity.getBbHeight()) * 32.0F), 128.0);

            try {
                Method getPlayersOfClassMethod = level.getClass().getMethod("getPlayersOfClass", Class.class, AABB.class);
                getPlayersOfClassMethod.setAccessible(true);
                ((Collection<ServerPlayer>)getPlayersOfClassMethod.invoke(level, ServerPlayer.class, entity.getBoundingBox().inflate(range))).forEach(player -> {
                    player.connection.send(removePacket);
                    level.getServer().execute(() -> player.connection.send(removePacket));
                });
            } catch (Exception var12) {
                try {
                    level.getPlayers(player -> player.distanceToSqr(entity) <= range * range).forEach(player -> player.connection.send(removePacket));
                } catch (Exception var11) {
                }
            }
        } catch (Exception var15) {
            LOGGER.debug("unexpected error", var15);
        }
    }

    private static void removeFromTransientManager(Entity entity, Level level) {
        try {
            if (level instanceof ClientLevel clientLevel) {
                TransientEntitySectionManager<Entity> manager = null;

                try {
                    Field entityManagerField = ClientLevel.class.getDeclaredField("entityManager");
                    entityManagerField.setAccessible(true);
                    manager = (TransientEntitySectionManager<Entity>)entityManagerField.get(clientLevel);
                } catch (Exception var25) {
                }

                if (manager == null) {
                    return;
                }

                EntitySection<Entity> section = null;

                try {
                    Field sectionsField = manager.getClass().getDeclaredField("sections");
                    sectionsField.setAccessible(true);
                    Object sections = sectionsField.get(manager);
                    if (sections instanceof Map) {
                        section = (EntitySection<Entity>)((Map)sections).get(entity.blockPosition().asLong());
                    }
                } catch (Exception var24) {
                }

                if (section == null) {
                    return;
                }

                ClassInstanceMultiMap<?> storage = null;

                try {
                    Field storageField = EntitySection.class.getDeclaredField("storage");
                    storageField.setAccessible(true);
                    storage = (ClassInstanceMultiMap<?>)storageField.get(section);
                } catch (Exception var23) {
                }

                if (storage != null) {
                    removeEntityFromSection(entity, storage);
                }

                try {
                    Method gatherAndRemoveEntitiesMethod = clientLevel.getClass().getMethod("gatherAndRemoveEntities");
                    gatherAndRemoveEntitiesMethod.invoke(clientLevel);
                } catch (Exception var22) {
                }

                try {
                    Field entityCacheField = clientLevel.getClass().getDeclaredField("entityCache");
                    entityCacheField.setAccessible(true);
                    Object entityCache = entityCacheField.get(clientLevel);
                    if (entityCache instanceof Map) {
                        ((Map)entityCache).remove(entity.getId());
                    }
                } catch (Exception var21) {
                }

                entity.onRemovedFromWorld();

                try {
                    Field partEntitiesField = ClientLevel.class.getDeclaredField("partEntities");
                    partEntitiesField.setAccessible(true);
                    Object partEntities = partEntitiesField.get(clientLevel);
                    if (partEntities instanceof Map) {
                        ((Map)partEntities).remove(entity.getId());
                    }
                } catch (Exception var20) {
                }

                entity.onRemovedFromWorld();
                MinecraftForge.EVENT_BUS.post(new EntityLeaveLevelEvent(entity, level));
                if (entity.isMultipartEntity()) {
                    for (PartEntity<?> part : entity.getParts()) {
                        try {
                            Field partEntitiesField = ClientLevel.class.getDeclaredField("partEntities");
                            partEntitiesField.setAccessible(true);
                            Object partEntities = partEntitiesField.get(clientLevel);
                            if (partEntities instanceof Map) {
                                ((Map)partEntities).remove(part.getId());
                            }
                        } catch (Exception var19) {
                        }
                    }
                }

                try {
                    Field visibleEntityStorageField = manager.getClass().getDeclaredField("f_157494_");
                    visibleEntityStorageField.setAccessible(true);
                    Object visibleEntityStorage = visibleEntityStorageField.get(manager);
                    if (visibleEntityStorage instanceof Map) {
                        ((Map)visibleEntityStorage).remove(entity.getUUID());
                    }
                } catch (Exception var18) {
                }

                try {
                    Field tickingEntitiesField = manager.getClass().getDeclaredField("f_157491_");
                    tickingEntitiesField.setAccessible(true);
                    Object tickingEntities = tickingEntitiesField.get(manager);
                    if (tickingEntities instanceof Map) {
                        ((Map)tickingEntities).remove(entity.getId());
                    }
                } catch (Exception var17) {
                }

                try {
                    Field entityInLevelCallbackField = Entity.class.getDeclaredField("f_146801_");
                    entityInLevelCallbackField.setAccessible(true);
                    entityInLevelCallbackField.set(entity, EntityInLevelCallback.NULL);
                } catch (Exception var16) {
                }

                try {
                    Field minecraftField = ClientLevel.class.getDeclaredField("minecraft");
                    minecraftField.setAccessible(true);
                    Object minecraft = minecraftField.get(clientLevel);
                    if (minecraft != null) {
                        Method getGuiMethod = minecraft.getClass().getMethod("gui");
                        Object gui = getGuiMethod.invoke(minecraft);
                        if (gui != null) {
                            Method getBossOverlayMethod = gui.getClass().getMethod("getBossOverlay");
                            BossHealthOverlay bossOverlay = (BossHealthOverlay)getBossOverlayMethod.invoke(gui);

                            try {
                                Field eventsField = BossHealthOverlay.class.getDeclaredField("events");
                                eventsField.setAccessible(true);
                                Object events = eventsField.get(bossOverlay);
                                if (events instanceof Map) {
                                    ((Map)events).remove(entity.getUUID());
                                }
                            } catch (Exception var14) {
                            }
                        }
                    }
                } catch (Exception var15) {
                }
            }
        } catch (Exception var26) {
            LOGGER.debug("unexpected error", var26);
        }
    }

    private static void removeFromPersistentManager(Entity entity, Level level) {
        try {
            if (level instanceof ServerLevel serverLevel) {
                PersistentEntitySectionManager<Entity> manager = null;

                try {
                    Field entityManagerField = ServerLevel.class.getDeclaredField("entityManager");
                    entityManagerField.setAccessible(true);
                    manager = (PersistentEntitySectionManager<Entity>)entityManagerField.get(serverLevel);
                } catch (Exception var23) {
                }

                if (manager == null) {
                    return;
                }

                EntitySection<Entity> section = null;

                try {
                    Field sectionsField = manager.getClass().getDeclaredField("sections");
                    sectionsField.setAccessible(true);
                    Object sections = sectionsField.get(manager);
                    if (sections instanceof Map) {
                        section = (EntitySection<Entity>)((Map)sections).get(SectionPos.asLong(entity.blockPosition()));
                    }
                } catch (Exception var22) {
                }

                if (section == null) {
                    return;
                }

                ClassInstanceMultiMap<?> storage = null;

                try {
                    Field storageField = EntitySection.class.getDeclaredField("storage");
                    storageField.setAccessible(true);
                    storage = (ClassInstanceMultiMap<?>)storageField.get(section);
                } catch (Exception var21) {
                }

                if (storage != null) {
                    removeEntityFromSection(entity, storage);
                }

                try {
                    Method gatherAndRemoveEntitiesMethod = serverLevel.getClass().getMethod("gatherAndRemoveEntities");
                    gatherAndRemoveEntitiesMethod.invoke(serverLevel);
                } catch (Exception var20) {
                }

                try {
                    Field entityCacheField = serverLevel.getClass().getDeclaredField("entityCache");
                    entityCacheField.setAccessible(true);
                    Object entityCache = entityCacheField.get(serverLevel);
                    if (entityCache instanceof Map) {
                        ((Map)entityCache).remove(entity.getId());
                    }
                } catch (Exception var19) {
                }

                try {
                    if (entity instanceof ServerPlayer serverplayer) {
                        Field playersField = ServerLevel.class.getDeclaredField("players");
                        playersField.setAccessible(true);
                        Object players = playersField.get(serverLevel);
                        if (players instanceof Collection) {
                            ((Collection)players).remove(serverplayer);
                        }
                    }
                } catch (Exception var18) {
                }

                try {
                    if (entity instanceof Mob mob) {
                        Field navigatingMobsField = ServerLevel.class.getDeclaredField("navigatingMobs");
                        navigatingMobsField.setAccessible(true);
                        Object navigatingMobs = navigatingMobsField.get(serverLevel);
                        if (navigatingMobs instanceof Collection) {
                            ((Collection)navigatingMobs).remove(mob);
                        }
                    }
                } catch (Exception var17) {
                }

                try {
                    if (entity.isMultipartEntity()) {
                        Field dragonPartsField = ServerLevel.class.getDeclaredField("dragonParts");
                        dragonPartsField.setAccessible(true);
                        Object dragonParts = dragonPartsField.get(serverLevel);
                        if (dragonParts instanceof Map) {
                            PartEntity[] parts = entity.getParts();

                            for (PartEntity<?> part : parts) {
                                ((Map)dragonParts).remove(part.getId());
                            }
                        }
                    }
                } catch (Exception var24) {
                }

                try {
                    Field dynamicGameEventListenerField = Entity.class.getDeclaredField("dynamicGameEventListener");
                    dynamicGameEventListenerField.setAccessible(true);
                    Object dynamicGameEventListener = dynamicGameEventListenerField.get(entity);
                    if (dynamicGameEventListener instanceof Iterable) {
                        ((Iterable)dynamicGameEventListener).forEach(listener -> {
                            try {
                                Method removeMethod = DynamicGameEventListener.class.getMethod("remove");
                                removeMethod.invoke(listener);
                            } catch (Exception var2x) {
                            }
                        });
                    }
                } catch (Exception var16) {
                }

                entity.onRemovedFromWorld();
                MinecraftForge.EVENT_BUS.post(new EntityLeaveLevelEvent(entity, level));

                try {
                    Field visibleEntityStorageField = manager.getClass().getDeclaredField("f_157494_");
                    visibleEntityStorageField.setAccessible(true);
                    Object visibleEntityStorage = visibleEntityStorageField.get(manager);
                    if (visibleEntityStorage instanceof Map) {
                        ((Map)visibleEntityStorage).remove(entity.getUUID());
                    }
                } catch (Exception var15) {
                }

                try {
                    Field tickingEntitiesField = manager.getClass().getDeclaredField("f_157491_");
                    tickingEntitiesField.setAccessible(true);
                    Object tickingEntities = tickingEntitiesField.get(manager);
                    if (tickingEntities instanceof Map) {
                        ((Map)tickingEntities).remove(entity.getId());
                    }
                } catch (Exception var14) {
                }

                try {
                    Field entityInLevelCallbackField = Entity.class.getDeclaredField("f_146801_");
                    entityInLevelCallbackField.setAccessible(true);
                    entityInLevelCallbackField.set(entity, EntityInLevelCallback.NULL);
                } catch (Exception var13) {
                }
            }
        } catch (Exception var25) {
            LOGGER.debug("unexpected error", var25);
        }
    }

    private static void removeBossEventViaReflection(Entity entity, ServerLevel level) {
        try {
            boolean isBoss = false;

            try {
                Method isBossMethod = Entity.class.getMethod("isBoss");
                isBoss = (Boolean)isBossMethod.invoke(entity);
            } catch (Exception var15) {
                try {
                    Field bossEventField = Entity.class.getDeclaredField("bossEvent");
                    bossEventField.setAccessible(true);
                    isBoss = bossEventField.get(entity) != null;
                } catch (Exception var14) {
                }
            }

            if (isBoss && !entity.isRemoved()) {
                Class<?> entityClass = entity.getClass();
                String[] possibleFieldNames = new String[]{"bossEvent", "f_31430_", "customBossEvents", "f_78466_", "f_129733_"};

                for (String fieldName : possibleFieldNames) {
                    try {
                        Field field = findFieldInClassHierarchy(entityClass, fieldName);
                        if (field != null) {
                            field.setAccessible(true);
                            if (field.get(entity) instanceof ServerBossEvent serverBossEvent) {
                                ClientboundBossEventPacket removePacket = ClientboundBossEventPacket.createRemovePacket(serverBossEvent.getId());
                                level.getServer().getPlayerList().getPlayers().forEach(player -> player.connection.send(removePacket));
                                if (canSafelySetToNull(entity)) {
                                    field.set(entity, null);
                                }
                            }
                        }
                    } catch (Exception var13) {
                    }
                }

                new Thread(() -> {
                    try {
                        Thread.sleep(2L);
                        level.getServer().execute(() -> clearAllBossEventsViaServer(level));
                    } catch (InterruptedException var2x) {
                        LOGGER.debug("unexpected error", var2x);
                    }
                }).start();
            }
        } catch (Exception var16) {
            LOGGER.debug("unexpected error", var16);
        }
    }

    public static void clearAllBossEventsViaServer(ServerLevel level) {
        try {
            MinecraftServer server = level.getServer();
            CustomBossEvents customBossEvents = server.getCustomBossEvents();

            for (Object bossEventObj : customBossEvents.getEvents()) {
                if (bossEventObj instanceof ServerBossEvent bossEvent) {
                    ClientboundBossEventPacket removePacket = ClientboundBossEventPacket.createRemovePacket(bossEvent.getId());
                    level.getServer().getPlayerList().getPlayers().forEach(player -> player.connection.send(removePacket));
                }
            }
        } catch (Exception var8) {
            LOGGER.debug("unexpected error", var8);
        }
    }

    private static boolean canSafelySetToNull(Entity entity) {
        return entity.isRemoved() || entity.level().isClientSide;
    }

    private static void forceUpdateEntityData(Entity entity, ServerLevel level) {
        try {
            ClientboundRemoveEntitiesPacket removePacket = new ClientboundRemoveEntitiesPacket(entity.getId());
            level.getServer().getPlayerList().getPlayers().forEach(player -> {
                player.connection.send(removePacket);
                level.getServer().execute(() -> player.connection.send(removePacket));
                level.getServer().execute(() -> level.getServer().execute(() -> player.connection.send(removePacket)));
            });
            level.getServer()
                .execute(
                    () -> level.getServer()
                        .getPlayerList()
                        .getPlayers()
                        .forEach(player -> player.connection.send(new ClientboundPlayerInfoUpdatePacket(Action.UPDATE_DISPLAY_NAME, player)))
                );
            clearEntityFromAllCollections(entity, level);
            MinecraftForge.EVENT_BUS.post(new EntityLeaveLevelEvent(entity, level));
            entity.onRemovedFromWorld();
            entity.kill();
            entity.remove(RemovalReason.DISCARDED);
        } catch (Exception var3) {
            LOGGER.debug("unexpected error", var3);
        }
    }

    private static void clearEntityFromAllCollections(Entity entity, ServerLevel level) {
        try {
            Field[] levelFields = ServerLevel.class.getDeclaredFields();

            for (Field field : levelFields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(level);
                    if (value instanceof Collection) {
                        ((Collection)value).remove(entity);
                    } else if (value instanceof Map<?, ?> map) {
                        map.values().remove(entity);
                        map.remove(entity.getId());
                        map.remove(entity.getUUID());
                    }
                } catch (Exception var9) {
                }
            }
        } catch (Exception var10) {
            LOGGER.debug("unexpected error", var10);
        }
    }

    private static Field findFieldInClassHierarchy(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException var3) {
                clazz = clazz.getSuperclass();
            }
        }

        return null;
    }

    public static <T> void removeEntityFromSection(Entity entity, ClassInstanceMultiMap<T> multiMap) {
        try {
            Field byClassField = ClassInstanceMultiMap.class.getDeclaredField("byClass");
            byClassField.setAccessible(true);
            if (byClassField.get(multiMap) instanceof Map<?, ?> map) {
                for (Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getValue() instanceof List) {
                        ((List)entry.getValue()).remove(entity);
                    }
                }
            }
        } catch (Exception var7) {
            LOGGER.debug("unexpected error", var7);
        }
    }

    private static void setEntityPos(Entity entity) {
        try {
            Vec3 v3 = new Vec3(entity.getX(), -9999.0, entity.getZ());
            entity.setPosRaw(v3.x, v3.y, v3.z);
            entity.moveTo(v3.x, v3.y, v3.z);
            entity.absMoveTo(v3.x, v3.y, v3.z);
        } catch (Exception var2) {
        }
    }

    private static void setEntityPosReflection(Entity target) {
        try {
            Vec3 v3 = new Vec3(target.getX(), -9999.0, target.getZ());
            Field positionField = Entity.class.getDeclaredField("position");
            positionField.setAccessible(true);
            positionField.set(target, v3);
            Field xoField = Entity.class.getDeclaredField("xo");
            xoField.setAccessible(true);
            xoField.setDouble(target, v3.x);
            Field yoField = Entity.class.getDeclaredField("yo");
            yoField.setAccessible(true);
            yoField.setDouble(target, v3.y);
            Field zoField = Entity.class.getDeclaredField("zo");
            zoField.setAccessible(true);
            zoField.setDouble(target, v3.z);
            target.setDeltaMovement(v3);
        } catch (Exception var6) {
        }
    }
}
