package com.buwan.shizuku.util.tooltip;

import net.minecraft.util.Mth;

import java.util.List;

public class ColorUtils {
    public static int getColorFromGradient(List<Integer> colors, float progress) {
        if (colors.isEmpty()) {
            return 16777215;
        } else if (colors.size() == 1) {
            return colors.get(0);
        } else {
            progress = Mth.clamp(progress, 0.0F, 0.999F);
            float scaledProgress = progress * (float) (colors.size() - 1);
            int index = (int) scaledProgress;
            float fraction = scaledProgress - (float) index;
            return index >= colors.size() - 1 ? colors.get(colors.size() - 1) : interpolateColor(colors.get(index), colors.get(index + 1), fraction);
        }
    }

    public static int interpolateColor(int color1, int color2, float progress) {
        progress = Mth.clamp(progress, 0.0F, 1.0F);
        int r1 = color1 >> 16 & 0xFF;
        int g1 = color1 >> 8 & 0xFF;
        int b1 = color1 & 0xFF;
        int r2 = color2 >> 16 & 0xFF;
        int g2 = color2 >> 8 & 0xFF;
        int b2 = color2 & 0xFF;
        int r = (int) ((float) r1 + (float) (r2 - r1) * progress);
        int g = (int) ((float) g1 + (float) (g2 - g1) * progress);
        int b = (int) ((float) b1 + (float) (b2 - b1) * progress);
        return r << 16 | g << 8 | b;
    }

    public static int HSBtoRGB(float hue, float saturation, float brightness) {
        int r = 0;
        int g = 0;
        int b = 0;
        if (saturation == 0.0F) {
            r = g = b = (int) (brightness * 255.0F + 0.5F);
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6.0F;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1.0F - saturation);
            float q = brightness * (1.0F - saturation * f);
            float t = brightness * (1.0F - saturation * (1.0F - f));
            switch ((int) h) {
                case 0:
                    r = (int) (brightness * 255.0F + 0.5F);
                    g = (int) (t * 255.0F + 0.5F);
                    b = (int) (p * 255.0F + 0.5F);
                    break;
                case 1:
                    r = (int) (q * 255.0F + 0.5F);
                    g = (int) (brightness * 255.0F + 0.5F);
                    b = (int) (p * 255.0F + 0.5F);
                    break;
                case 2:
                    r = (int) (p * 255.0F + 0.5F);
                    g = (int) (brightness * 255.0F + 0.5F);
                    b = (int) (t * 255.0F + 0.5F);
                    break;
                case 3:
                    r = (int) (p * 255.0F + 0.5F);
                    g = (int) (q * 255.0F + 0.5F);
                    b = (int) (brightness * 255.0F + 0.5F);
                    break;
                case 4:
                    r = (int) (t * 255.0F + 0.5F);
                    g = (int) (p * 255.0F + 0.5F);
                    b = (int) (brightness * 255.0F + 0.5F);
                    break;
                case 5:
                    r = (int) (brightness * 255.0F + 0.5F);
                    g = (int) (p * 255.0F + 0.5F);
                    b = (int) (q * 255.0F + 0.5F);
            }
        }

        return r << 16 | g << 8 | b;
    }

    public static int adjustColorBrightness(int color, float brightness) {
        brightness = Mth.clamp(brightness, 0.0F, 2.0F);
        int r = (int) ((float) (color >> 16 & 0xFF) * brightness);
        int g = (int) ((float) (color >> 8 & 0xFF) * brightness);
        int b = (int) ((float) (color & 0xFF) * brightness);
        r = Math.min(255, Math.max(0, r));
        g = Math.min(255, Math.max(0, g));
        b = Math.min(255, Math.max(0, b));
        return r << 16 | g << 8 | b;
    }
}
