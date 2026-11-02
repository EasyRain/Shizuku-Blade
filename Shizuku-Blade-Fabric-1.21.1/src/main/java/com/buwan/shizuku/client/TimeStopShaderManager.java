package com.buwan.shizuku.client;





public class TimeStopShaderManager {
    private static boolean shaderActive = false;
    private static float targetStrength = 0.0F;
    private static float currentStrength = 0.0F;
    private static long lastUpdateTime = 0L;
    private static final float TRANSITION_SPEED = 3.0F;

    public static void syncFromServer(boolean active, float strength) {
        shaderActive = active;
        targetStrength = active ? strength : 0.0F;
        if (lastUpdateTime == 0L) {
            lastUpdateTime = System.currentTimeMillis();
        }
    }

    public static void updateStrength() {
        if (Math.abs(currentStrength - targetStrength) > 0.001F) {
            long currentTime = System.currentTimeMillis();
            if (lastUpdateTime == 0L) {
                lastUpdateTime = currentTime;
            }

            float deltaTime = (float) (currentTime - lastUpdateTime) / 1000.0F;
            lastUpdateTime = currentTime;
            float maxChange = TRANSITION_SPEED * deltaTime;
            if (currentStrength < targetStrength) {
                currentStrength = Math.min(targetStrength, currentStrength + maxChange);
            } else {
                currentStrength = Math.max(targetStrength, currentStrength - maxChange);
            }
        }
    }

    public static float getCurrentStrength() {
        return currentStrength;
    }

    public static float getTargetStrength() {
        return targetStrength;
    }

    public static boolean isActive() {
        return shaderActive || currentStrength > 0.001F;
    }

    public static void reset() {
        shaderActive = false;
        targetStrength = 0.0F;
        currentStrength = 0.0F;
        lastUpdateTime = 0L;
    }

    public static float[] getTimeStopTint() {
        float strength = getCurrentStrength();
        float r = 1.0F;
        float g = 0.84F;
        float b = 0.0F;
        return new float[]{lerp(1.0F, r, strength), lerp(1.0F, g, strength), lerp(1.0F, b, strength)};
    }

    public static float getBrightnessMultiplier() {
        float strength = getCurrentStrength();
        return lerp(1.0F, 1.1F, strength);
    }

    public static float getSaturationMultiplier() {
        float strength = getCurrentStrength();
        return lerp(1.0F, 1.2F, strength);
    }

    private static float lerp(float start, float end, float alpha) {
        return start + (end - start) * alpha;
    }
}
