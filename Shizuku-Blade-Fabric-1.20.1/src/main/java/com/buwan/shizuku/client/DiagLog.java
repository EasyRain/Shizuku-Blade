package com.buwan.shizuku.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 临时诊断：每个 tag 只打印前若干条，避免每帧刷屏。
 * 定位完（时停 / 囚牢 / 星河领域都可见后）整类删除。
 */
public final class DiagLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("shizuku-field");
    private static final java.util.Map<String, Integer> COUNTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int LIMIT = 6;

    /** 临时：把时停球强制画成不透明品红 + 关深度测试，用来判定"到底有没有画到屏幕上"。定位完改回 false。 */
    public static boolean DEBUG_BRIGHT = false;

    /** 临时：强制走 GUI 阶段重绘（原本只给光影兜底用），用来判定世界阶段 vs GUI 阶段。 */
    public static boolean DEBUG_GUI = false;

    private DiagLog() {
    }

    /** 打印矩阵里能看出问题的几个分量（平移 + 缩放）。 */
    public static String fmt(org.joml.Matrix4f m) {
        if (m == null) {
            return "null";
        }
        return String.format("t=(%.1f,%.1f,%.1f) s=(%.2f,%.2f,%.2f)", m.m30(), m.m31(), m.m32(), m.m00(), m.m11(), m.m22());
    }

    public static void once(String tag, String msg, Object... args) {
        int n = COUNTS.merge(tag, 1, Integer::sum);
        if (n <= LIMIT) {
            LOGGER.info("[shizuku-field] {} #{} {}", tag, n, String.format(msg, args));
        }
    }
}
