package com.buwan.shizuku.util;

/**
 * SA 伤害调试输出（默认关闭）。
 *
 * <p>加了固定倍率之后，伤害数字在游戏里不直观（还要过护甲/附魔/暴击）。想核对具体数值时，
 * 给 JVM 加参数 {@code -Dshizuku.sa.debug=true}，日志里就会打印每一发 SA 实际设置的伤害值。</p>
 */
public final class SaDebug {
    private static final boolean ENABLED = Boolean.getBoolean("shizuku.sa.debug");
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("shizuku/sa");

    private SaDebug() {}

    public static void log(String saName, double damage) {
        if (ENABLED) {
            LOGGER.info("[sa-damage] {} -> {}", saName, damage);
        }
    }
}
