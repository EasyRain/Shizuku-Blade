package com.buwan.shizuku.sa;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;

/** A single encounter UUID may be shared by a core, its phases, a summoner and its SavedData owner. */
@EventBusSubscriber(modid = "shizuku")
public final class JudgmentEncounters {
    public static final String ENCOUNTER_ID = "shizuku:encounter_id";

    @AutoRegisterCapability
    public interface Controller {
        UUID encounterId();
        /** Release this encounter's bars, phase queue and persistence. Must be idempotent. */
        void finish(ServerLevel level);
    }

    public static final Capability<Controller> CONTROLLER = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ClassValue<List<Field>> BARS = new ClassValue<>() {
        @Override protected List<Field> computeValue(Class<?> type) {
            var fields = new ArrayList<Field>();
            for (Class<?> c = type; c != null && Entity.class.isAssignableFrom(c); c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && ServerBossEvent.class.isAssignableFrom(field.getType())
                            && field.trySetAccessible()) fields.add(field);
                }
            }
            return List.copyOf(fields);
        }
    };

    /** Register during common setup. Adapters can resolve encounter identities from their own SavedData. */
    public interface Adapter {
        default boolean isBoss(Entity entity) { return false; }
        @Nullable default UUID encounterId(Entity entity) { return null; }
        default void beforeRemoval(ServerLevel level, Entity entity) {}
        default void finish(ServerLevel level, Entity entity) {}
        default void visitControllers(ServerLevel level, AABB area, Consumer<Controller> visitor) {}
    }

    private static final Map<String, Adapter> ADAPTERS = new ConcurrentHashMap<>();
    static {
        registerAdapter("supersteve", new Adapter() {
            public boolean isBoss(Entity e) { return e instanceof net.minecraft.world.entity.LivingEntity l && SuperSteveCompat.isBoss(l); }
            public void beforeRemoval(ServerLevel level, Entity e) {
                if (e instanceof net.minecraft.world.entity.LivingEntity l && SuperSteveCompat.isBoss(l)) SuperSteveCompat.execute(l);
            }
        });
        registerAdapter("chaoswither", new Adapter() {
            public boolean isBoss(Entity e) { return Ymsa.isChaosWither(e); }
            public void finish(ServerLevel level, Entity e) {
                if (isBoss(e)) Ymsa.finishChaosEncounter(level, e);
            }
        });
    }

    public static void registerAdapter(String namespace, Adapter adapter) {
        if (ADAPTERS.putIfAbsent(namespace, java.util.Objects.requireNonNull(adapter)) != null)
            throw new IllegalArgumentException("Duplicate judgment adapter: " + namespace);
    }

    @Nullable static Adapter adapter(Entity entity) {
        var type = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return type == null ? null : ADAPTERS.get(type.getNamespace());
    }

    private JudgmentEncounters() {}

    static UUID link(ICapabilityProvider provider, @Nullable CompoundTag data) {
        var controller = provider.getCapability(CONTROLLER).resolve();
        if (controller.isPresent()) return controller.get().encounterId();
        if (provider instanceof Entity entity) {
            Adapter adapter = adapter(entity);
            if (adapter != null) {
                UUID id = adapter.encounterId(entity);
                if (id != null) return id;
            }
        }
        if (data == null) return null;
        if (data.hasUUID(ENCOUNTER_ID)) return data.getUUID(ENCOUNTER_ID);
        for (String key : JudgmentConfig.ENCOUNTER_KEYS.get()) if (data.hasUUID(key)) return data.getUUID(key);
        return null;
    }

    static boolean identifies(Entity entity) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null) return false;
        if (entity.getType().is(Ymsa.BOSS_TYPES)) return true;
        Adapter adapter = adapter(entity);
        if (adapter != null && adapter.isBoss(entity)) return true;
        if (entity.getCapability(CONTROLLER).isPresent() || !BARS.get(entity.getClass()).isEmpty()) return true;
        if (JudgmentConfig.TYPES.get().contains(key.toString()) || JudgmentConfig.NAMESPACES.get().contains(key.getNamespace())) return true;
        String customName = entity.hasCustomName() && entity.getCustomName() != null ? entity.getCustomName().getString() : "";
        String name = (key.getPath() + " " + entity.getClass().getSimpleName() + " " + customName).toLowerCase(Locale.ROOT);
        return JudgmentConfig.KEYWORDS.get().stream().anyMatch(word -> name.contains(word.toLowerCase(Locale.ROOT)));
    }

    static void finishEntity(ServerLevel level, Entity entity) {
        Adapter adapter = adapter(entity);
        if (adapter != null) adapter.finish(level, entity);
        entity.getCapability(CONTROLLER).ifPresent(c -> c.finish(level));
        for (Field f : BARS.get(entity.getClass())) {
            try {
                if (f.get(entity) instanceof ServerBossEvent bar) {
                    bar.setVisible(false);
                    bar.removeAllPlayers();
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static void collectControllers(ServerLevel level, AABB area, UUID caster, UnleashedVerdicts verdicts) {
        for (Adapter adapter : ADAPTERS.values()) {
            adapter.visitControllers(level, area, controller -> {
                verdicts.recordController(caster, level, controller.encounterId(), area.getCenter());
                controller.finish(level);
            });
        }
        for (int x = (int)Math.floor(area.minX / 16); x <= (int)Math.floor(area.maxX / 16); x++) {
            for (int z = (int)Math.floor(area.minZ / 16); z <= (int)Math.floor(area.maxZ / 16); z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (BlockEntity block : List.copyOf(chunk.getBlockEntities().values())) {
                    if (!area.contains(block.getBlockPos().getCenter())) continue;
                    block.getCapability(CONTROLLER).ifPresent(controller -> {
                        verdicts.recordController(caster, level, controller.encounterId(), block.getBlockPos().getCenter());
                        controller.finish(level);
                    });
                }
            }
        }
    }

    @SubscribeEvent public static void verifyCompatibility(ServerStartingEvent event) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("chaoswither")) return;
        var log = org.slf4j.LoggerFactory.getLogger("shizuku/judgment");
        for (String target : List.of("net.wzz.chaoswither.utils.ChaosUtil",
                "lIllIlIIllIIllI.IlIIIlIlllIllIll.lIIIIllIIIIIIIl.IllIIIIIIIllIIIl.IIlIlIlllllIIII")) {
            try {
                Class.forName(target, false, JudgmentEncounters.class.getClassLoader());
                // JudgmentMixinAudit logs the actual handler call sites on transformation.
                return;
            } catch (ClassNotFoundException missingVersion) {
                continue;
            }
        }
        log.warn("Unrecognized chaoswither controller version; check the installed mod's encounter hooks.");
    }

    @SubscribeEvent public static void onConversion(net.minecraftforge.event.entity.living.LivingConversionEvent.Post event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            UnleashedVerdicts.get(level.getServer()).followConversion(event.getEntity(), event.getOutcome());
        }
    }

    @SubscribeEvent public static void onSpawn(MobSpawnEvent.FinalizeSpawn event) {
        var spawner = event.getSpawner();
        if (spawner == null || !(event.getEntity().level() instanceof ServerLevel level)) return;
        BlockEntity block = spawner.getSpawnerBlockEntity();
        Entity cart = spawner.getSpawnerEntity();
        if (block == null && cart == null) return;
        CompoundTag data = block != null ? block.getPersistentData() : cart.getPersistentData();
        UUID source = link(block != null ? block : cart, data);
        if (source == null) {
            source = UUID.randomUUID();
            data.putUUID(ENCOUNTER_ID, source);
            if (block != null) block.setChanged();
        }
        event.getEntity().getPersistentData().putUUID("shizuku:spawn_source", source);
        if (UnleashedVerdicts.get(level.getServer()).active(source, level.getServer().overworld().getGameTime()))
            event.setSpawnCancelled(true);
    }

    @SubscribeEvent public static void onSummonerUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel() instanceof ServerLevel level && blockedItem(level, event.getItemStack()))
            event.setCanceled(true);
    }

    static boolean blockedItem(ServerLevel level, ItemStack stack) {
        UUID id = link(stack, stack.getTag());
        return id != null && UnleashedVerdicts.get(level.getServer()).active(id, level.getServer().overworld().getGameTime());
    }

    @SubscribeEvent public static void onSummonerBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (blockedItem(level, event.getItemStack())) {
            event.setCanceled(true);
            return;
        }
        var block = level.getBlockEntity(event.getPos());
        if (block == null) return;
        UUID id = link(block, block.getPersistentData());
        if (id != null && UnleashedVerdicts.get(level.getServer()).active(id, level.getServer().overworld().getGameTime()))
            event.setCanceled(true);
    }
}
