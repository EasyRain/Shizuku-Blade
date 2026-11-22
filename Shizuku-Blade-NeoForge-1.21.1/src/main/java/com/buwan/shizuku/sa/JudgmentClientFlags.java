package com.buwan.shizuku.sa;

/**
 * 客户端向服务端无关代码传递的「审判覆盖」快照。
 *
 * <p>1.21 NeoForge 没有 {@code DistExecutor}，而服务端类只要引用客户端类（Minecraft/LocalPlayer）
 * 就会在专用服务器上抛 "Attempted to load class ... for invalid dist DEDICATED_SERVER"。
 * 所以这里用一个**不含任何客户端 import** 的中立容器：客户端每 tick 把
 * 「解放开关 + 玩家位置」写进来，服务端侧只读它。</p>
 */
public final class JudgmentClientFlags {
    private static volatile boolean covers;
    private static volatile double x, y, z;
    private static final double RADIUS = 320.0;

    private JudgmentClientFlags() {}

    /** 客户端调用：把当前玩家位置与解放开关写进来。 */
    public static void update(boolean coverageEnabled, double px, double py, double pz) {
        covers = coverageEnabled;
        x = px;
        y = py;
        z = pz;
    }

    public static void clear() {
        covers = false;
    }

    /** 服务端调用：该点在客户端报告的覆盖球内吗（客户端没上报时恒 false）。 */
    public static boolean covers(double px, double py, double pz) {
        if (!covers) return false;
        double dx = px - x, dy = py - y, dz = pz - z;
        return dx * dx + dy * dy + dz * dz <= RADIUS * RADIUS;
    }
}
