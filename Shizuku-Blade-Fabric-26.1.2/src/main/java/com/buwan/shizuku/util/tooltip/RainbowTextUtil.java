package com.buwan.shizuku.util.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RainbowTextUtil {
    private static final Map<String, MutableComponent> STATIC_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 500;
    private static long lastCacheCleanup = 0L;
    private static final Set<AnimationType> STATIC_EFFECTS = Set.of(AnimationType.LINEAR);
    public static final List<Integer> Rainbow = Arrays.asList(
            16711680, 16744192, 16776960, 8388352, 65280, 65407, 65535, 32767, 255, 4129023, 8323327, 16711935, 16711807, 16711680
    );
    public static final List<Integer> FIRE_COLORS = Arrays.asList(16711680, 16729344, 16747520, 16766720, 16775388);
    public static final List<Integer> OCEAN_COLORS = Arrays.asList(128, 255, 49151, 8900331, 14743295);
    public static final List<Integer> PURPLE_COLORS = Arrays.asList(4915330, 9055202, 10040012, 12211667, 15132410);
    public static final List<Integer> ICE_COLORS = Arrays.asList(8900331, 4286945, 255, 1644912);
    public static final List<Integer> GOLD_COLORS = Arrays.asList(16766720, 16753920, 16747520, 14329120);

    public static MutableComponent createRainbowText(String text, RainbowConfig config) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        } else if (STATIC_EFFECTS.contains(config.animationType)) {
            String cacheKey = generateCacheKey(text, config);
            MutableComponent cached = getFromCache(cacheKey);
            if (cached != null) {
                return cached.copy();
            } else {
                MutableComponent result = generateGradientText(text, config);
                putToCache(cacheKey, result);
                return result.copy();
            }
        } else {
            return generateGradientText(text, config);
        }
    }

    public static MutableComponent createRainbowText(String text, AnimationType type, List<Integer> colors) {
        RainbowConfig config = new RainbowConfig().setAnimationType(type).setColors(colors);
        return createRainbowText(text, config);
    }

    public static MutableComponent rainbow(String text) {
        return createRainbowText(text, new RainbowConfig().setAnimationType(AnimationType.RAINBOW));
    }

    public static MutableComponent rainbow(String text, float speed) {
        return createRainbowText(text, new RainbowConfig().setAnimationType(AnimationType.RAINBOW).setSpeed(speed));
    }

    public static MutableComponent breathing(String text, List<Integer> colors) {
        return createRainbowText(text, new RainbowConfig().setAnimationType(AnimationType.BREATHING).setColors(colors));
    }

    public static MutableComponent breathing(String text, List<Integer> colors, float speed) {
        return createRainbowText(
                text, new RainbowConfig().setAnimationType(AnimationType.BREATHING).setColors(colors).setSpeed(speed)
        );
    }

    public static MutableComponent wave(String text, List<Integer> colors) {
        return createRainbowText(text, new RainbowConfig().setAnimationType(AnimationType.WAVE).setColors(colors));
    }

    public static MutableComponent wave(String text, List<Integer> colors, float speed) {
        return createRainbowText(text, new RainbowConfig().setAnimationType(AnimationType.WAVE).setColors(colors).setSpeed(speed));
    }

    public static MutableComponent blink(String text, int color1, int color2) {
        return createRainbowText(text, new RainbowConfig().setAnimationType(AnimationType.BLINK).setColors(color1, color2));
    }

    public static MutableComponent blink(String text, int color1, int color2, float speed) {
        return createRainbowText(
                text, new RainbowConfig().setAnimationType(AnimationType.BLINK).setColors(color1, color2).setSpeed(speed)
        );
    }

    public static MutableComponent randomBlink(String text, List<Integer> colors) {
        return createRainbowText(text, new RainbowConfig().setAnimationType(AnimationType.RANDOM_BLINK).setColors(colors));
    }

    public static MutableComponent shimmer(String text, List<Integer> colors) {
        return createRainbowText(text, new RainbowConfig().setAnimationType(AnimationType.SHIMMER).setColors(colors));
    }

    public static MutableComponent electric(String text, List<Integer> colors) {
        return createRainbowText(text, new RainbowConfig().setAnimationType(AnimationType.ELECTRIC).setColors(colors));
    }

    public static MutableComponent flowing(String text, List<Integer> colors) {
        return createRainbowText(text, new RainbowConfig().setAnimationType(AnimationType.FLOWING).setColors(colors));
    }

    public static MutableComponent flowing(String text, List<Integer> colors, float speed) {
        return createRainbowText(
                text, new RainbowConfig().setAnimationType(AnimationType.FLOWING).setColors(colors).setSpeed(speed)
        );
    }

    private static MutableComponent generateGradientText(String text, RainbowConfig config) {
        long time = getTime();
        MutableComponent result = Component.empty();
        int textLength = text.length();
        if (config.colors.isEmpty()) {
            config.colors = Arrays.asList(16777215);
        }

        for (int i = 0; i < textLength; i++) {
            char character = text.charAt(i);
            if (character == ' ') {
                result.append(Component.translatable(" "));
            } else {
                int color = calculateCharacterColor(i, textLength, time, config);
                result.append(Component.translatable(String.valueOf(character)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
            }
        }

        return result;
    }

    private static int calculateCharacterColor(int charIndex, int textLength, long time, RainbowConfig config) {
        return switch (config.animationType) {
            case LINEAR -> calculateLinearGradient(charIndex, textLength, time, config);
            case RAINBOW -> calculateRainbowGradient(charIndex, time, config);
            case BREATHING -> calculateBreathingEffect(charIndex, time, config);
            case WAVE -> calculateWaveEffect(charIndex, textLength, time, config);
            case PULSE -> calculatePulseEffect(charIndex, time, config);
            case BLINK -> calculateBlinkEffect(time, config);
            case RANDOM_BLINK -> calculateRandomBlinkEffect(charIndex, time, config);
            case FADE_IN_OUT -> calculateFadeInOutEffect(charIndex, textLength, time, config);
            case SHIMMER -> calculateShimmerEffect(charIndex, textLength, time, config);
            case ELECTRIC -> calculateElectricEffect(charIndex, time, config);
            case FLOWING -> calculateFlowingEffect(charIndex, time, config);
            default -> config.colors.get(0);
        };
    }

    private static int calculateLinearGradient(int charIndex, int textLength, long time, RainbowConfig config) {
        float normalizedIndex = (float) charIndex / config.colorSpan;
        float progress = normalizedIndex % 1.0F;
        if (config.reverse) {
            progress = 1.0F - progress;
        }

        return ColorUtils.getColorFromGradient(config.colors, progress);
    }

    private static int calculateRainbowGradient(int charIndex, long time, RainbowConfig config) {
        float baseHue = (float) charIndex / config.colorSpan;
        float hue = ((float) time * config.speed * 0.02F + baseHue * config.charSpacing) % 1.0F;
        if (config.reverse) {
            hue = 1.0F - hue;
        }

        return ColorUtils.HSBtoRGB(hue, config.intensity, 1.0F);
    }

    private static int calculateBreathingEffect(int charIndex, long time, RainbowConfig config) {
        float breathe = (float) (Math.sin((float) time * config.speed * 0.05F + (float) charIndex / config.colorSpan) * 0.5 + 0.5);
        breathe = Mth.clamp(breathe, 0.3F, 1.0F);
        int colorIndex = (int) ((float) charIndex / config.colorSpan) % config.colors.size();
        int baseColor = config.colors.get(colorIndex);
        return ColorUtils.adjustColorBrightness(baseColor, breathe * config.intensity);
    }

    private static int calculateWaveEffect(int charIndex, int textLength, long time, RainbowConfig config) {
        float wave = (float) Math.sin(
                (float) time * config.speed * 0.1F + (float) charIndex / config.colorSpan * config.charSpacing * Math.PI * 2.0
        );
        wave = (wave * 0.5F + 0.5F) * config.intensity;
        if (config.reverse) {
            wave = 1.0F - wave;
        }

        return ColorUtils.getColorFromGradient(config.colors, wave);
    }

    private static int calculateBlinkEffect(long time, RainbowConfig config) {
        boolean isOn = (float) time * config.speed * 0.1F % 2.0F < 1.0F;
        if (config.reverse) {
            isOn = !isOn;
        }

        return isOn ? config.colors.get(0) : (config.colors.size() > 1 ? config.colors.get(1) : 0);
    }

    private static int calculatePulseEffect(int charIndex, long time, RainbowConfig config) {
        float pulse = (float) Math.abs(Math.sin((float) time * config.speed * 0.1F + (float) charIndex / config.colorSpan));
        pulse = Math.min(1.0F, pulse * config.intensity);
        int colorIndex = (int) ((float) charIndex / config.colorSpan) % config.colors.size();
        int baseColor = config.colors.get(colorIndex);
        int accentColor = config.colors.size() > 1 ? config.colors.get((colorIndex + 1) % config.colors.size()) : baseColor;
        return ColorUtils.interpolateColor(baseColor, accentColor, pulse);
    }

    private static int calculateRandomBlinkEffect(int charIndex, long time, RainbowConfig config) {
        int group = (int) ((float) charIndex / config.colorSpan);
        Random random = new Random((long) group + (long) ((float) time * config.speed * 0.1F));
        return config.colors.get(random.nextInt(config.colors.size()));
    }

    private static int calculateFadeInOutEffect(int charIndex, int textLength, long time, RainbowConfig config) {
        float normalizedIndex = (float) charIndex / config.colorSpan;
        float progress = ((float) time * config.speed * 0.05F + normalizedIndex * config.charSpacing) % 2.0F;
        float fade = progress > 1.0F ? 2.0F - progress : progress;
        if (config.reverse) {
            fade = 1.0F - fade;
        }

        int colorIndex = (int) normalizedIndex % config.colors.size();
        int baseColor = config.colors.get(colorIndex);
        return ColorUtils.adjustColorBrightness(baseColor, fade * config.intensity);
    }

    private static int calculateShimmerEffect(int charIndex, int textLength, long time, RainbowConfig config) {
        float shimmer = (float) Math.max(
                0.0, Math.sin((float) time * config.speed * 0.15F - (float) charIndex / config.colorSpan * config.charSpacing * 2.0F)
        );
        shimmer = (float) Math.pow(shimmer, 3.0) * config.intensity;
        int colorIndex = (int) ((float) charIndex / config.colorSpan) % config.colors.size();
        int baseColor = config.colors.get(colorIndex);
        int shimmerColor = config.colors.size() > 1 ? config.colors.get((colorIndex + 1) % config.colors.size()) : 16777215;
        return ColorUtils.interpolateColor(baseColor, shimmerColor, shimmer);
    }

    private static int calculateElectricEffect(int charIndex, long time, RainbowConfig config) {
        int group = (int) ((float) charIndex / config.colorSpan);
        Random random = new Random((long) (group * 31) + (long) ((float) time * config.speed * 0.5F));
        float electric = random.nextFloat();
        if (electric < 0.1F * config.intensity) {
            return 16777215;
        } else if (electric < 0.3F * config.intensity) {
            int colorIndex = group % config.colors.size();
            return config.colors.get(Math.min(colorIndex + 1, config.colors.size() - 1));
        } else {
            int colorIndex = group % config.colors.size();
            return config.colors.get(colorIndex);
        }
    }

    private static int calculateFlowingEffect(int charIndex, long time, RainbowConfig config) {
        float normalizedIndex = (float) charIndex / config.colorSpan;
        float progress = ((float) time * config.speed * 0.03F + normalizedIndex * config.charSpacing) % (float) config.colors.size();
        if (config.reverse) {
            progress = (float) config.colors.size() - progress;
        }

        return interpolateColor(config.colors, progress);
    }

    private static int interpolateColor(List<Integer> colors, float progress) {
        if (colors.isEmpty()) {
            return 16777215;
        } else if (colors.size() == 1) {
            return colors.get(0);
        } else {
            progress = Math.max(0.0F, Math.min((float) colors.size() - 0.001F, progress));
            int index = (int) progress;
            float fraction = progress - (float) index;
            if (index >= colors.size() - 1) {
                return colors.get(colors.size() - 1);
            } else {
                int color1 = colors.get(index);
                int color2 = colors.get(index + 1);
                return ColorUtils.interpolateColor(color1, color2, fraction);
            }
        }
    }

    private static String generateCacheKey(String text, RainbowConfig config) {
        return text
                + "|"
                + config.animationType
                + "|"
                + config.colors.hashCode()
                + "|"
                + config.speed
                + "|"
                + config.intensity
                + "|"
                + config.charSpacing
                + "|"
                + config.reverse;
    }

    private static MutableComponent getFromCache(String key) {
        cleanupCache();
        return STATIC_CACHE.get(key);
    }

    private static void putToCache(String key, MutableComponent component) {
        if (STATIC_CACHE.size() >= MAX_CACHE_SIZE) {
            STATIC_CACHE.clear();
        }

        STATIC_CACHE.put(key, component);
    }

    private static void cleanupCache() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheCleanup > 60000L) {
            if (STATIC_CACHE.size() > 250) {
                STATIC_CACHE.clear();
            }

            lastCacheCleanup = currentTime;
        }
    }

    private static long getTime() {
        // 与 1.20.1 原版一致：用客户端世界的 gameTime（数值小，float 精度足够驱动流动动画）。
        // 之前用 System.currentTimeMillis()/50（约 3.5e10），转 float 后精度只剩千位，
        // 时间项对颜色循环完全失效 → 文字静态、霓虹位移特效不生效。
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : System.currentTimeMillis() / 50L;
    }

    public static void clearCache() {
        STATIC_CACHE.clear();
    }

    public enum AnimationType {
        LINEAR,
        RAINBOW,
        BREATHING,
        WAVE,
        PULSE,
        BLINK,
        RANDOM_BLINK,
        FADE_IN_OUT,
        SHIMMER,
        ELECTRIC,
        FLOWING
    }

    public static class RainbowConfig {
        public List<Integer> colors = Arrays.asList(16711680, 16744192, 16776960, 65280, 255, 4915330, 9699539);
        public float speed = 1.0F;
        public float intensity = 1.0F;
        public float charSpacing = 0.1F;
        public float colorSpan = 1.0F;
        public boolean reverse = false;
        public AnimationType animationType = AnimationType.RAINBOW;

        public RainbowConfig setColorSpan(float colorSpan) {
            this.colorSpan = Math.max(0.1F, colorSpan);
            return this;
        }

        public RainbowConfig setColors(Integer... colors) {
            this.colors = Arrays.asList(colors);
            return this;
        }

        public RainbowConfig setColors(List<Integer> colors) {
            this.colors = colors;
            return this;
        }

        public RainbowConfig setSpeed(float speed) {
            this.speed = speed;
            return this;
        }

        public RainbowConfig setIntensity(float intensity) {
            this.intensity = intensity;
            return this;
        }

        public RainbowConfig setCharSpacing(float charSpacing) {
            this.charSpacing = charSpacing;
            return this;
        }

        public RainbowConfig setReverse(boolean reverse) {
            this.reverse = reverse;
            return this;
        }

        public RainbowConfig setAnimationType(AnimationType type) {
            this.animationType = type;
            return this;
        }
    }
}
