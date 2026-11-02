package com.buwan.shizuku.client.screen;

import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 雫工作台 GUI 背景的飘落花瓣动画（1.21.1 移植）。
 */
public class FallingPetalRenderer {

    private static final Random RNG = new Random();
    private static final List<Particle> PARTICLES = new ArrayList<>();
    private static final List<Identifier> TEXTURES = new ArrayList<>();
    private static final int MAX_VARIANTS = 8;
    private static final int PARTICLE_COUNT = 50;
    private static boolean useFallback = true;

    private FallingPetalRenderer() {
    }

    public static void init(int screenWidth, int screenHeight) {
        TEXTURES.clear();
        ResourceManager manager = Minecraft.getInstance().getResourceManager();

        for (int i = 0; i < MAX_VARIANTS; i++) {
            Identifier loc = shizuku.prefix("textures/gui/falling/petal_" + i + ".png");
            if (manager.getResource(loc).isPresent()) {
                TEXTURES.add(loc);
            }
        }

        useFallback = TEXTURES.isEmpty();
        restart(screenWidth, screenHeight);
    }

    public static void restart(int screenWidth, int screenHeight) {
        PARTICLES.clear();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            PARTICLES.add(spawnParticle(screenWidth, screenHeight, true));
        }
    }

    public static void tick(int screenWidth, int screenHeight) {
        for (int i = 0; i < PARTICLES.size(); i++) {
            Particle p = PARTICLES.get(i);
            p.age++;
            p.y = p.y + p.vy;
            p.x = p.x + p.vx;
            p.rot = p.rot + p.rotSpeed;
            if (p.y > (double) ((float) screenHeight + p.size + 20.0F)) {
                PARTICLES.set(i, spawnParticle(screenWidth, screenHeight, false));
            }
        }
    }

    public static void render(GuiGraphicsExtractor guiGraphics, int screenWidth, int screenHeight, float partialTick) {
        if (!PARTICLES.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            if (!useFallback) {
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
            }

            for (Particle p : PARTICLES) {
                double renderX = p.x + p.vx * (double) partialTick;
                double renderY = p.y + p.vy * (double) partialTick;
                float alpha = 1.0F;
                int fadeIn = 30;
                int fadeOut = 60;
                if (p.age < fadeIn) {
                    alpha = (float) p.age / (float) fadeIn;
                } else if (p.age > p.maxLife - fadeOut) {
                    alpha = Math.max(0.0F, (float) (p.maxLife - p.age) / (float) fadeOut);
                }

                alpha *= p.alpha;
                if (!(alpha < 0.01F)) {
                    int size = (int) p.size;
                    if (useFallback) {
                        renderFallback(guiGraphics, renderX, renderY, size, p.rot, p.color, alpha);
                    } else {
                        renderTexture(guiGraphics, renderX, renderY, size, p.rot, p.getTexture(), alpha);
                    }
                }
            }

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void renderTexture(GuiGraphicsExtractor guiGraphics, double x, double y, int size, float rot,
                                      Identifier tex, float alpha) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0);
        pose.mulPose(Axis.ZP.rotation(rot));
        int half = size / 2;
        guiGraphics.blit(tex, -half, -half, size, size, 0.0F, 0.0F, 16, 16, 16, 16);
        pose.popPose();
    }

    private static void renderFallback(GuiGraphicsExtractor guiGraphics, double x, double y, int size, float rot,
                                       int color, float alpha) {
        int a = (int) (alpha * 255.0F) & 0xFF;
        int argb = a << 24 | color & 16777215;
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0);
        pose.mulPose(Axis.ZP.rotation(rot));
        int half = size / 2;
        guiGraphics.fill(-half, -half, half, half, argb);
        pose.popPose();
    }

    private static Particle spawnParticle(int screenWidth, int screenHeight, boolean atAnyHeight) {
        Particle p = new Particle();
        p.x = RNG.nextDouble() * (double) (screenWidth + 40) - 20.0;
        if (atAnyHeight) {
            p.y = -RNG.nextDouble() * (double) screenHeight * 0.5 - 20.0;
        } else {
            p.y = -RNG.nextDouble() * 30.0 - 20.0;
        }

        float speedBase = (float) screenHeight * 0.012F + RNG.nextFloat() * 0.6F;
        p.vy = (double) (speedBase * (0.8F + RNG.nextFloat() * 0.6F));
        p.vx = (double) ((RNG.nextFloat() - 0.5F) * 0.4F);
        p.size = 10.0F + RNG.nextFloat() * 10.0F;
        p.rot = RNG.nextFloat() * (float) (Math.PI * 2);
        p.rotSpeed = (RNG.nextFloat() - 0.5F) * 0.06F;
        p.alpha = 0.5F + RNG.nextFloat() * 0.5F;
        p.maxLife = 300 + RNG.nextInt(400);
        int r = 220 + RNG.nextInt(36);
        int g = 150 + RNG.nextInt(80);
        int b = 180 + RNG.nextInt(60);
        p.color = r << 16 | g << 8 | b;
        if (!TEXTURES.isEmpty()) {
            p.texIndex = RNG.nextInt(TEXTURES.size());
        }
        return p;
    }

    public static void invalidate() {
        TEXTURES.clear();
    }

    public static class Particle {
        double x;
        double y;
        double vx;
        double vy;
        float rot;
        float rotSpeed;
        float size;
        float alpha;
        int age;
        int maxLife;
        int color;
        int texIndex;

        public Identifier getTexture() {
            return TEXTURES.isEmpty() ? null : TEXTURES.get(this.texIndex % TEXTURES.size());
        }
    }
}
