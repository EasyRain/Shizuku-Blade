package com.buwan.shizuku.core;

/**
 * HealthReturnPlugin 的开关与返回值配置（int/float/double 三种常量形态），
 * 由配置文件/指令切换，方便线上排查「是哪个 mod 在顶血量」。
 */
public class HealthReturnSwitch {
    private static volatile boolean enabled = false;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean status) {
        enabled = status;
    }

    public static Integer getIntValue() {
        return enabled ? 0 : null;
    }

    public static Float getFloatValue() {
        return enabled ? 0.0F : null;
    }

    public static Double getDoubleValue() {
        return enabled ? 0.0 : null;
    }
}
