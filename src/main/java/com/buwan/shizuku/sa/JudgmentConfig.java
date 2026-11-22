package com.buwan.shizuku.sa;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

public final class JudgmentConfig {
    public static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.IntValue RETENTION, PHASE_WINDOW, RADIUS;
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> TYPES, NAMESPACES, KEYWORDS, ENCOUNTER_KEYS;
    /** 喵石掉落概率（百分比）。放在同一个 spec 里，因为 Forge 1.20.1 一个 mod 同类型只能有一个默认文件名的配置。 */
    static final ForgeConfigSpec.IntValue MEOW_STONE_DROP_PERCENT;

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
        builder.push("drops");
        MEOW_STONE_DROP_PERCENT = builder
            .comment("Chance (percent, 0-100) that a hostile mob drops a Meow Stone on death. 0 disables it.")
            .defineInRange("meowStoneDropPercent", 1, 0, 100);
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

    // ---- 喵石掉落 ----

    public static int meowStoneDropPercent() {
        return MEOW_STONE_DROP_PERCENT.get();
    }

    public static void meowStoneDropPercent(int percent) {
        MEOW_STONE_DROP_PERCENT.set(Math.max(0, Math.min(100, percent)));
        MEOW_STONE_DROP_PERCENT.save();
    }

    /** 给掉落判定用的 0..1 概率；配置没载入时退回默认 1%。 */
    public static float meowStoneDropChance() {
        int percent;
        try {
            percent = SPEC.isLoaded() ? MEOW_STONE_DROP_PERCENT.get() : 1;
        } catch (Exception ignored) {
            percent = 1;
        }
        return percent / 100.0F;
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
