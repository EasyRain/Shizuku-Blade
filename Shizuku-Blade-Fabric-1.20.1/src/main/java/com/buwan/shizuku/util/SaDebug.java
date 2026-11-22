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

    /**
     * 连「攻击力基准」一起打印，方便跨版本对比：
     * {@code damage} 是倍率，{@code attack} 是当前攻击力，{@code basis = attack × 倍率}。
     * 两个版本 basis 一样、最终伤害不同 ⇒ 差异在 MC/前置的伤害管线，不在本 mod。
     */
    public static void log(String saName, double damage, net.minecraft.world.entity.LivingEntity caster) {
        if (!ENABLED || caster == null) {
            log(saName, damage);
            return;
        }
        double attack = caster.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        LOGGER.info("[sa-damage] {} 倍率={} 攻击力={} 基准={}", saName,
                String.format("%.2f", damage), String.format("%.2f", attack),
                String.format("%.2f", damage * attack));
    }
}
