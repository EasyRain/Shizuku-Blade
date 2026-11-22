package com.buwan.shizuku.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 本模组的通用服务端配置（目前只有喵石掉落概率）。
 *
 * <p>与 {@code sa.JudgmentConfig} 分开：那个是审判玩法专用的，这里是杂项，
 * 以后加别的掉落/概率项也往这里放，避免审判配置越滚越大。</p>
 */
public final class ShizukuConfig {

    public static final ForgeConfigSpec SPEC;
    /** 喵石掉落概率，按百分比存（0~100），方便在配置页里用整数步进。 */
    static final ForgeConfigSpec.IntValue MEOW_STONE_DROP_PERCENT;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("drops");
        MEOW_STONE_DROP_PERCENT = builder
            .comment("Chance (percent, 0-100) that a hostile mob drops a Meow Stone on death. 0 disables it.")
            .defineInRange("meowStoneDropPercent", 1, 0, 100);
        builder.pop();
        SPEC = builder.build();
    }

    private ShizukuConfig() {}

    /** 配置是否已载入（多人客户端上没有服务端配置，读值会抛异常）。 */
    public static boolean isLoaded() {
        return SPEC.isLoaded();
    }

    public static int meowStoneDropPercent() {
        return MEOW_STONE_DROP_PERCENT.get();
    }

    public static void meowStoneDropPercent(int percent) {
        MEOW_STONE_DROP_PERCENT.set(Math.max(0, Math.min(100, percent)));
        MEOW_STONE_DROP_PERCENT.save();
    }

    /** 给掉落判定用的 0..1 概率；配置没载入时退回默认的 1%。 */
    public static float meowStoneDropChance() {
        int percent;
        try {
            percent = SPEC.isLoaded() ? MEOW_STONE_DROP_PERCENT.get() : 1;
        } catch (Exception ignored) {
            percent = 1;
        }
        return percent / 100.0F;
    }
}
