package com.buwan.shizuku.sa;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

public final class JudgmentConfig {
    public static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.IntValue RETENTION, PHASE_WINDOW, RADIUS;
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> TYPES, NAMESPACES, KEYWORDS, ENCOUNTER_KEYS;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("unleashedJudgment");
        RETENTION = builder.comment("Server ticks to retain a target or encounter identity.")
            .defineInRange("observationTicks", 24000, 1, Integer.MAX_VALUE);
        PHASE_WINDOW = builder.comment("Ticks to associate a nearby boss without an encounter identity.")
            .defineInRange("phaseTicks", 200, 0, Integer.MAX_VALUE);
        RADIUS = builder.defineInRange("phaseRadius", 24, 1, 320);
        TYPES = builder.defineListAllowEmpty("bossTypes", List.of(), v -> v instanceof String s && !s.isBlank());
        NAMESPACES = builder.defineListAllowEmpty("bossNamespaces", List.of(), v -> v instanceof String s && !s.isBlank());
        KEYWORDS = builder.defineListAllowEmpty("bossNameKeywords", List.of("boss"), v -> v instanceof String s && !s.isBlank());
        ENCOUNTER_KEYS = builder.comment("Additional UUID fields on entity ForgeData or summoner item NBT identifying a shared boss encounter.")
            .defineListAllowEmpty("encounterUuidKeys", List.of(), v -> v instanceof String s && !s.isBlank());
        builder.pop();
        SPEC = builder.build();
    }

    /** 配置是否已被 Forge 载入（多人客户端上没有服务端配置，读值会抛异常）。 */
    public static boolean isLoaded() {
        return SPEC.isLoaded();
    }

    public static int observationTicks() {
        return RETENTION.get();
    }

    public static void observationTicks(int value) {
        RETENTION.set(value);
        RETENTION.save();
    }

    public static int phaseTicks() {
        return PHASE_WINDOW.get();
    }

    public static void phaseTicks(int value) {
        PHASE_WINDOW.set(value);
        PHASE_WINDOW.save();
    }

    public static int phaseRadius() {
        return RADIUS.get();
    }

    public static void phaseRadius(int value) {
        RADIUS.set(value);
        RADIUS.save();
    }

    /** 列表类配置的只读摘要（这些只能在配置文件里改）。 */
    public static String listSummary() {
        if (!SPEC.isLoaded()) {
            return "-";
        }
        return "bossTypes=" + TYPES.get().size()
            + ", namespaces=" + NAMESPACES.get().size()
            + ", keywords=" + KEYWORDS.get().size()
            + ", encounterKeys=" + ENCOUNTER_KEYS.get().size();
    }

    private JudgmentConfig() {}
}
