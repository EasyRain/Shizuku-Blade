package com.buwan.shizuku.sa;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import com.buwan.shizuku.util.PersistentData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
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

/** A single encounter UUID may be shared by a core, its phases, a summoner and its SavedData owner. */
public final class JudgmentEncounters {
    public static final String ENCOUNTER_ID = "shizuku:encounter_id";

    /** Fabric 上没有 Forge 的能力系统：实现这个接口的实体/方块就是「遭遇控制器」，用 instanceof 判断。 */
    public interface Controller {
        UUID encounterId();
        /** Release this encounter's bars, phase queue and persistence. Must be idempotent. */
        void finish(ServerLevel level);
    }

    static java.util.Optional<Controller> controller(Object holder) {
        return holder instanceof Controller controller ? java.util.Optional.of(controller) : java.util.Optional.empty();
    }
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
        var type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return type == null ? null : ADAPTERS.get(type.getNamespace());
    }

    private JudgmentEncounters() {}

    static UUID link(Object provider, @Nullable CompoundTag data) {
        var controller = controller(provider);
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
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key == null) return false;
        if (entity.getType().is(Ymsa.BOSS_TYPES)) return true;
        Adapter adapter = adapter(entity);
        if (adapter != null && adapter.isBoss(entity)) return true;
        if (entity instanceof Controller || !BARS.get(entity.getClass()).isEmpty()) return true;
        if (JudgmentConfig.TYPES.get().contains(key.toString()) || JudgmentConfig.NAMESPACES.get().contains(key.getNamespace())) return true;
        String customName = entity.hasCustomName() && entity.getCustomName() != null ? entity.getCustomName().getString() : "";
        String name = (key.getPath() + " " + entity.getClass().getSimpleName() + " " + customName).toLowerCase(Locale.ROOT);
        return JudgmentConfig.KEYWORDS.get().stream().anyMatch(word -> name.contains(word.toLowerCase(Locale.ROOT)));
    }

    static void finishEntity(ServerLevel level, Entity entity) {
        Adapter adapter = adapter(entity);
        if (adapter != null) adapter.finish(level, entity);
        if (entity instanceof Controller controller) controller.finish(level);
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
                    if (block instanceof Controller controller) {
                        verdicts.recordController(caster, level, controller.encounterId(), block.getBlockPos().getCenter());
                        controller.finish(level);
                    }
                }
            }
        }
    }

    /** 由 Fabric 的 SERVER_STARTING 调用。 */
    public static void verifyCompatibility() {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("chaoswither")) return;
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

    /** 由生物转化回调调用（Fabric 无 Forge 的 LivingConversionEvent，改由调用方传参）。 */
    public static void onConversion(net.minecraft.world.entity.Mob entity, net.minecraft.world.entity.Mob outcome) {
        if (entity.level() instanceof ServerLevel level) {
            UnleashedVerdicts.get(level.getServer()).followConversion(entity, outcome);
        }
    }

    /**
     * 生成器刷新生物时打遭遇标记（Forge 是 MobSpawnEvent.FinalizeSpawn）。
     * Fabric 1.20.1 没有等价的 spawn 事件，这里保留方法给将来的 mixin/调用方使用。
     */
    public static void onSpawn(ServerLevel level, @Nullable BlockEntity block,
                               @Nullable Entity summonerEntity, Entity spawned) {
        if (block == null && summonerEntity == null) return;
        CompoundTag data = block != null ? PersistentData.of(block) : PersistentData.of(summonerEntity);
        UUID source = link(block != null ? block : summonerEntity, data);
        if (source == null) {
            source = UUID.randomUUID();
            data.putUUID(ENCOUNTER_ID, source);
            if (block != null) block.setChanged();
        }
        PersistentData.of(spawned).putUUID("shizuku:spawn_source", source);
        if (UnleashedVerdicts.get(level.getServer()).active(source, level.getServer().overworld().getGameTime())) {
            spawned.discard();
        }
    }

    /** 召唤物物品被右键时若属于「正在被审判」的遭遇则拦下（Fabric 的 UseItemCallback 调用）。 */
    public static boolean onSummonerUse(ServerLevel level, ItemStack stack) {
        return blockedItem(level, stack);
    }

    static boolean blockedItem(ServerLevel level, ItemStack stack) {
        UUID id = link(stack, stack.getTag());
        return id != null && UnleashedVerdicts.get(level.getServer()).active(id, level.getServer().overworld().getGameTime());
    }

    /** 召唤器方块被右键时同样拦下（Fabric 的 UseBlockCallback 调用）。 */
    public static boolean onSummonerBlock(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (blockedItem(level, stack)) {
            return true;
        }
        var block = level.getBlockEntity(pos);
        if (block == null) return false;
        UUID id = link(block, PersistentData.of(block));
        return id != null && UnleashedVerdicts.get(level.getServer()).active(id, level.getServer().overworld().getGameTime());
    }
}
