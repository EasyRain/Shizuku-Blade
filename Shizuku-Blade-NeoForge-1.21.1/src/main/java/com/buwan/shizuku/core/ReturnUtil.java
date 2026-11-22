package com.buwan.shizuku.core;

/**
 * AllReturnPlugin / HealthReturnPlugin 的全局开关（allReturn / mixinAllReturn /
 * eventAllReturn 三个维度），默认全关。
 */
public class ReturnUtil {
    private static boolean allReturn = false;
    private static boolean mixinAllReturn = false;
    private static boolean eventAllReturn = false;

    public static boolean isAllReturn() {
        return allReturn;
    }

    public static boolean isEventAllReturn() {
        return eventAllReturn;
    }

    public static boolean isMixinAllReturn() {
        return mixinAllReturn;
    }

    public static boolean setAllReturn(boolean allReturn) {
        ReturnUtil.allReturn = allReturn;
        mixinAllReturn = allReturn;
        return true;
    }

    public static boolean setEventAllReturn(boolean allReturn) {
        eventAllReturn = allReturn;
        return true;
    }

    public static boolean setMixinAllReturn(boolean mixinAllReturn) {
        return true;
    }
}
