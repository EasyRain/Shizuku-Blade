package com.buwan.shizuku.core;

/**
 * 审判处决的跨层开关。
 *
 * 只有解放审判处于「过载」窗口（Ymsa.doYmsaSlash 里 arm / finally disarm）时
 * Unsafe 层才会真正动手，平时一律 false，不污染任何常规战斗。
 *
 * 注意：这里刻意只保留一个 volatile 布尔，不做 WeakHashMap 标记表 ——
 * 原先给 Agent 字节码注入用的对象标记已经随 Agent 层一起移除了。
 */
public final class JudgmentBridge {
    private static volatile boolean armed = false;

    private JudgmentBridge() {}

    public static void arm(boolean value) {
        armed = value;
    }

    public static boolean armed() {
        return armed;
    }

    /** 供调试输出：当前开关状态。 */
    public static String describe() {
        return "armed=" + armed;
    }
}
