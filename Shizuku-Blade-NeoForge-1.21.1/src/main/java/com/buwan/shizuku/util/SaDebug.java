package com.buwan.shizuku.util;

/**
 * SA 伤害调试输出。
 *
 * <p>⚠️ <b>临时测试代码</b>：目前**无条件输出**（用户反馈 -D 参数在自己的启动器里没生效），
 * 为了能直接对比两个版本的伤害基准。测试完把 {@link #ENABLED} 改回
 * {@code Boolean.getBoolean("shizuku.sa.debug")}，或者直接删掉 {@code SaDebug.log(...)} 调用即可。</p>
 */
public final class SaDebug {
    /** 临时：无条件打印。正式版应改回 Boolean.getBoolean("shizuku.sa.debug")。 */
    private static final boolean ENABLED = true;
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
        if (!ENABLED) {
            return;
        }
        if (caster == null) {
            log(saName, damage);
            return;
        }
        double attack = caster.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        LOGGER.info("[sa-damage] {} 倍率={} 攻击力={} 基准={}", saName,
                String.format("%.2f", damage), String.format("%.2f", attack),
                String.format("%.2f", damage * attack));
    }
}
