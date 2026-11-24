package com.buwan.shizuku.client;

import com.buwan.shizuku.sa.Ymsa;
import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * 「审判」面板预览里的空间裂缝 —— {@code judgment_rift.fsh} 的逐行 CPU 移植。
 *
 * <p>背景：26.1 的 GUI 是「渲染状态提取」（GuiRenderState 只有 blit/渐变/物品/文本等
 * 预制状态），跑不了自定义 GLSL。此前的矩形拼贴观感是「一条条方块色带」（2026-11-24
 * 用户实测反馈）。这里把 fsh 的 hash/noise/stars/glass/夜空/极光/latch 全部纯数学函数
 * 原样搬到 Java，逐像素渲染进一张 128×72 的 {@link DynamicTexture}，再 blit 进预览区
 * —— <b>与世界里的裂缝同一套公式</b>，观感天然一致，且随 {@code tick} 实时动画。</p>
 *
 * <p>CPU 成本控制：只在预览页可见且 {@code tick} 前进超过 0.08（≈4 游戏）时才重算一帧
 * （裂缝动画慢，视觉上无损），其余帧直接 blit 缓存纹理。重算一帧 ≈ 9k 像素 × 数十次
 * 三角函数，实测毫秒级，仅在面板打开期间发生。</p>
 */
final class JudgmentRiftPreview {
    /** CPU 渲染分辨率（宽高比 1.78 = fsh 的 p 空间 (uv*2-1)*vec2(1.78,1)）。 */
    private static final int W = 128;
    private static final int H = 72;
    /** 帧间隔：tick 前进超过此值才重算（渲染贴图）；两帧之间直接 blit 缓存。 */
    private static final float REFRESH_TICK_STEP = 0.08F;
    /** fwidth 的 CPU 替代：stroke 的抗锯齿宽度（原版 max(fwidth(d),0.0013)）。 */
    private static final float AA = 0.004F;

    private static final Identifier TEXTURE_ID =
            shizuku.prefix("textures/gui/judgment_rift_preview");

    private static DynamicTexture texture;
    private static boolean registered;
    private static float renderedTick = Float.NaN;

    private JudgmentRiftPreview() {
    }

    /** 预览裂缝（与世界的 {@code JudgmentVisuals#renderWorld} 同一套时间轴）。 */
    static void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float tick) {
        // 与 fsh 的整体 discard 一致：打击(8)前、闭合(38)后无画面
        if (tick < Ymsa.STRIKE_TICK || tick >= Ymsa.RIFT_CLOSED_TICK || width <= 1 || height <= 1) {
            return;
        }
        if (!ensureTexture()) {
            return;
        }
        if (Float.isNaN(renderedTick) || Math.abs(tick - renderedTick) >= REFRESH_TICK_STEP) {
            renderedTick = tick;
            NativeImage image = texture.getPixels();
            for (int py = 0; py < H; py++) {
                float v = (py + 0.5F) / H;
                for (int px = 0; px < W; px++) {
                    float u = (px + 0.5F) / W;
                    image.setPixel(px, py, riftPixel(u, v, tick));
                }
            }
            texture.upload();
        }
        graphics.blit(TEXTURE_ID, x, y, x + width, y + height, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    /** GUI 渲染线程上懒建 + 注册动态贴图（一次）。 */
    private static boolean ensureTexture() {
        try {
            if (texture == null) {
                texture = new DynamicTexture(
                        () -> "shizuku judgment rift preview", new NativeImage(W, H, true));
            }
            if (!registered) {
                Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
                registered = true;
            }
            return true;
        } catch (Throwable t) {
            return false; // 预览缺一张图不致命：面板照常
        }
    }

    // ---------------- judgment_rift.fsh 的 Java 逐行移植 ----------------
    // 以下函数名与 fsh 完全一致，方便对照；GLSL 的 vec2 拆成两个 float 参数。

    private static int riftPixel(float u, float v, float t) {
        float impact = Ymsa.STRIKE_TICK;
        float lock = Ymsa.RIFT_CLOSED_TICK;
        float px = (u * 2.0F - 1.0F) * 1.78F;
        float py = v * 2.0F - 1.0F;
        float strike = smoothstep(impact, impact + 3.2F, t);
        if (t < impact || t >= lock) {
            return 0;
        }
        float close = smoothstep(23.0F, lock, t);

        // axis = normalize(vec2(1.0, 0.34))
        float len = (float) Math.sqrt(1.0F + 0.34F * 0.34F);
        float ax = 1.0F / len;
        float ay = 0.34F / len;
        float qx = px * ax + py * ay;
        float qy = px * -ay + py * ax;
        if (Math.abs(qx) > 1.73F || Math.abs(qy) > 0.83F) {
            return 0;
        }
        float taper = pow(Math.max(0.0F, 1.0F - pow(Math.abs(qx) / 1.72F, 1.6F)), 0.83F);
        float reach = mix(-1.79F, 1.86F, strike);
        float draw = 1.0F - smoothstep(reach - 0.09F, reach, qx);
        float live = step(impact, t) * (1.0F - smoothstep(lock - 0.35F, lock + 0.4F, t));
        float jag = (noise(qx * 9.0F, 1.0F) - 0.5F) * 0.115F
                + (noise(qx * 31.0F, 3.0F) - 0.5F) * 0.045F;
        float mid = taper * (jag + 0.025F * (float) Math.sin(qx * 6.0F + t * 0.16F) * (1.0F - close));
        float open = smoothstep(impact, impact + 8.0F, t) * (1.0F - close);
        float halfWidth = taper * open * (0.26F + 0.12F * noise(qx * 5.0F, 2.0F));
        float edge = Math.abs(qy - mid) - halfWidth;
        float span = draw * live * smoothstep(0.0F, 0.08F, taper);
        float spread = taper * open * (0.19F + 0.12F * noise(qx * 3.0F, 8.0F));
        float band = span * (1.0F - smoothstep(0.0F, Math.max(0.001F, spread), Math.max(edge, 0.0F)));

        // 直排-alpha 预乘合成（fsh 的 over()）
        float[] color = new float[4];

        // g = vec2(q.x*5.3 + sin(q.y*9)*0.20, (q.y-mid)*6.0); g.y -= sign(q.y-mid)*open*(...)
        float gx = qx * 5.3F + (float) Math.sin(qy * 9.0F) * 0.20F;
        float gy = (qy - mid) * 6.0F - Math.signum(qy - mid) * open * (0.41F + 0.27F * (float) Math.sin(qx * 4.0F));
        float face = glass(gx, gy);
        float crack = GLASS_OUT[0];
        // over(color, mix(vec3(0.10,0.14,0.26), vec3(0.60,0.79,0.94), plate.y), band*(0.11+0.36*pow(plate.y,3)))
        // over(color, vec3(0.68,0.84,1.0), crack*band*0.86)
        over(color, 0.10F + 0.50F * face, 0.14F + 0.65F * face, 0.26F + 0.68F * face,
                band * (0.11F + 0.36F * face * face * face));
        over(color, 0.68F, 0.84F, 1.0F, crack * band * 0.86F);

        if (edge < 0.008F && span > 0.0F) {
            float inside = (1.0F - smoothstep(-0.005F, 0.004F, edge)) * span;
            float skyx = qx;
            float skyy = (qy - mid) / Math.max(halfWidth, 0.018F) * 0.39F;
            float r = (float) Math.sqrt((skyx * 0.8F) * (skyx * 0.8F) + skyy * skyy);
            float swirl = 0.36F / (r + 0.37F) + t * 0.012F;
            float cs = (float) Math.cos(swirl);
            float sn = (float) Math.sin(swirl);
            float scale = 1.0F + t * 0.011F;
            float rsx = (cs * skyx - sn * skyy) * scale;
            float rsy = (sn * skyx + cs * skyy) * scale;
            float cloud = noise(rsx * 5.0F, rsy * 5.0F) * 0.65F + noise(rsx * 14.0F, rsy * 14.0F) * 0.35F;
            float lane = exp(-7.0F * Math.abs(rsy - 0.18F * (float) Math.sin(rsx * 4.0F)));
            float nr = 0.013F + cloud * 0.08F;
            float ng = 0.012F + cloud * 0.11F;
            float nb = 0.063F + cloud * 0.27F;
            float nm = noise(rsx * 3.0F, rsy * 3.0F);
            nr += lane * cloud * mix(0.13F, 0.79F, nm);
            ng += lane * cloud * mix(0.52F, 0.27F, nm);
            nb += lane * cloud * mix(0.76F, 0.65F, nm);
            float aurora = exp(-12.0F * Math.abs(rsy - 0.21F * (float) Math.sin(rsx * 5.1F + t * 0.043F)
                    - 0.12F * (float) Math.sin(rsx * 2.1F)));
            float aglow = aurora * (0.35F + cloud);
            nr += aglow * mix(0.18F, 0.93F, cloud);
            ng += aglow * mix(0.80F, 0.39F, cloud);
            nb += aglow * mix(0.91F, 0.80F, cloud);
            float twinkle = stars(rsx, rsy, 18.0F) + 0.46F * stars(rsx + 2.6F, rsy + 2.6F, 37.0F);
            nr += twinkle * 0.80F;
            ng += twinkle * 0.89F;
            nb += twinkle;
            over(color, nr, ng, nb, inside);
        }
        float rim = Math.abs(edge);
        over(color, 0.27F, 0.49F, 0.99F, exp(-32.0F * rim) * 0.66F * span);
        over(color, mix(0.73F, 1.0F, close), mix(0.91F, 0.81F, close), mix(1.0F, 0.53F, close),
                (1.0F - smoothstep(0.005F + 0.003F * open, 0.005F + 0.003F * open + AA, rim)) * span);
        float latch = (1.0F - smoothstep(0.0F, 2.2F, Math.abs(t - lock)))
                * (1.0F - smoothstep(0.0F, 0.58F, Math.abs(qx)));
        over(color, 1.0F, 0.88F, 0.59F, latch * exp(-155.0F * Math.abs(qy - mid)));
        if (color[3] < 0.003F) {
            return 0;
        }
        float inv = 1.0F / color[3];
        return ARGB.colorFromFloat(color[3], color[0] * inv, color[1] * inv, color[2] * inv);
    }

    /**
     * fsh 的 glass()：最近两个不规则晶格。返回面的随机数（plate.y）；
     * {@code crack = stroke(plate.x, 0.020)} 写进 {@link #GLASS_OUT}[0]（band 在调用处乘）。
     */
    private static final float[] GLASS_OUT = new float[2];

    private static float glass(float p0, float p1) {
        float cx = (float) Math.floor(p0);
        float cy = (float) Math.floor(p1);
        float fx = frac(p0);
        float fy = frac(p1);
        float first = 9.0F;
        float second = 9.0F;
        float face = 0.0F;
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                float qx = i + 0.18F + 0.64F * hash(cx + i, cy + j) - fx;
                float qy = j + 0.18F + 0.64F * hash(cx + i + 4.1F, cy + j + 4.1F) - fy;
                float d = qx * qx + qy * qy;
                if (d < first) {
                    second = first;
                    first = d;
                    face = hash(cx + i + 7.9F, cy + j + 7.9F);
                } else {
                    second = Math.min(second, d);
                }
            }
        }
        // crack = stroke(plate.x, 0.020) = 1 - smoothstep(0.020, 0.020+fwidth, 间距)
        GLASS_OUT[0] = 1.0F - smoothstep(0.020F, 0.020F + AA, second - first);
        return face;
    }

    /** fsh 的 over()：预乘空间直排 alpha 合成。color = {r,g,b,a}（预乘）。 */
    private static void over(float[] color, float ir, float ig, float ib, float opacity) {
        float a = Math.max(0.0F, Math.min(1.0F, opacity));
        color[0] = ir * a + color[0] * (1.0F - a);
        color[1] = ig * a + color[1] * (1.0F - a);
        color[2] = ib * a + color[2] * (1.0F - a);
        color[3] = a + color[3] * (1.0F - a);
    }

    private static float hash(float p0, float p1) {
        return frac((float) Math.sin(p0 * 127.1F + p1 * 311.7F) * 43758.5453F);
    }

    private static float noise(float p0, float p1) {
        float cx = (float) Math.floor(p0);
        float cy = (float) Math.floor(p1);
        float fx = frac(p0);
        float fy = frac(p1);
        fx = fx * fx * (3.0F - 2.0F * fx);
        fy = fy * fy * (3.0F - 2.0F * fy);
        float a = hash(cx, cy);
        float b = hash(cx + 1.0F, cy);
        float c = hash(cx, cy + 1.0F);
        float d = hash(cx + 1.0F, cy + 1.0F);
        return mix(mix(a, b, fx), mix(c, d, fx), fy);
    }

    private static float stars(float p0, float p1, float density) {
        float idx = (float) Math.floor(p0 * density);
        float idy = (float) Math.floor(p1 * density);
        float fpx = frac(p0 * density);
        float fpy = frac(p1 * density);
        float dx = fpx - (0.18F + 0.64F * hash(idx + 2.2F, idy + 2.2F));
        float dy = fpy - (0.18F + 0.64F * hash(idx + 5.6F, idy + 5.6F));
        float radius2 = dx * dx + dy * dy;
        return step(0.62F, hash(idx, idy)) * exp(-radius2 * 110.0F);
    }

    // ---------------- 基础数学（与 GLSL 语义一致） ----------------

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static float step(float edge, float x) {
        return x < edge ? 0.0F : 1.0F;
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = Math.max(0.0F, Math.min(1.0F, (x - e0) / (e1 - e0)));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float pow(float base, float exponent) {
        return (float) Math.pow(base, exponent);
    }

    private static float exp(float x) {
        return (float) Math.exp(x);
    }
}
