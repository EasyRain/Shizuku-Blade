package com.buwan.shizuku.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;


import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SynthwaveShaderManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SynthwaveShaderManager.class);
    public static ShaderInstance synthwave;
    private static boolean isSynthwaveEnabled;
    private static long animationStartTime;
    private static final float SPEED_MULTIPLIER = 3.0F;
    /** 1.21.1 专属：特效开启后前 0.2 秒完全透明（快速连点右键不可见），随后 0.1 秒内淡入。
     *  原版无淡入，连续右键脚下会持续闪烁（上游 bug，原作者未修）。 */
    private static final long FADE_DELAY_MS = 200L;
    private static final long FADE_MS = 100L;

    /** Fabric：懒创建合成波着色器（NeoForge 有 RegisterShadersEvent，Fabric 没有）。 */
    public static ShaderInstance ensureShader() {
        if (synthwave == null) {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc == null || mc.getResourceManager() == null) {
                    return null;
                }
                synthwave = new ShaderInstance(mc.getResourceManager(),
                        "synthwave",
                        DefaultVertexFormat.POSITION_TEX);
                LOGGER.info("Synthwave shader created");
            } catch (Exception e) {
                LOGGER.error("Failed to create Synthwave shader", e);
                return null;
            }
        }
        return synthwave;
    }

    public static void render(PoseStack poseStack, Matrix4f projectionMatrix) {
        if (isSynthwaveEnabled) {
            if (ShaderPackCompat.isShaderPackActive()) {
                // 光影（Iris/Oculus）下跳过：世界阶段渲染会污染光影管线（整屏花屏），
                // 而 GUI 阶段重绘没有深度信息，着色器只剩纯红黑底色，效果不可接受。
                // 与 1.20.1 原版在 Oculus 下的表现一致 —— 特效不可见但不破坏画面，
                // SA 逻辑（服务端审判/抹除）不受影响。
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                ensureShader();
                if (synthwave == null) {
                    LOGGER.warn("Synthwave shader is null!");
                } else {
                    LOGGER.debug("Rendering Synthwave shader...");
                    RenderSystem.backupProjectionMatrix();
                    RenderSystem.disableDepthTest();
                    RenderSystem.depthMask(false);
                    RenderSystem.enableBlend();
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                    int depthTextureId = -1;
                    int currentFBO = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
                    if (currentFBO != 0) {
                        int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
                        if (type == GL30.GL_TEXTURE_2D) {
                            depthTextureId = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                        } else if (type == 0) {
                            type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
                            if (type == GL30.GL_TEXTURE_2D) {
                                depthTextureId = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                            }
                        }
                    }

                    if (depthTextureId <= 0) {
                        depthTextureId = mc.getMainRenderTarget().getDepthTextureId();
                    }

                    RenderSystem.setShader(() -> synthwave);
                    synthwave.setSampler("MainDepthSampler", depthTextureId);
                    if (synthwave.getUniform("ScreenSize") != null) {
                        synthwave.getUniform("ScreenSize").set((float) mc.getWindow().getGuiScaledWidth(), (float) mc.getWindow().getGuiScaledHeight());
                    }

                    Matrix4f modelViewMatrix = poseStack.last().pose();
                    Matrix4f inverseProj = new Matrix4f(projectionMatrix);
                    Matrix4f inverseModelView = new Matrix4f(modelViewMatrix);
                    inverseProj.invert();
                    inverseModelView.invert();
                    if (synthwave.getUniform("U_InverseProjectionMatrix") != null) {
                        synthwave.getUniform("U_InverseProjectionMatrix").set(inverseProj);
                    }

                    if (synthwave.getUniform("U_InverseViewMatrix") != null) {
                        synthwave.getUniform("U_InverseViewMatrix").set(inverseModelView);
                    }

                    Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
                    if (synthwave.getUniform("U_CameraPosition") != null) {
                        synthwave.getUniform("U_CameraPosition").set((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
                    }

                    if (synthwave.getUniform("U_GameTime") != null) {
                        float timeSeconds = (float) (System.currentTimeMillis() - animationStartTime) / 1000.0F * SPEED_MULTIPLIER;
                        synthwave.getUniform("U_GameTime").set(timeSeconds);
                    }

                    if (synthwave.getUniform("U_FadeIn") != null) {
                        long elapsed = System.currentTimeMillis() - animationStartTime;
                        float fade = (float) (elapsed - FADE_DELAY_MS) / (float) FADE_MS;
                        synthwave.getUniform("U_FadeIn").set(Math.max(Math.min(fade, 1.0F), 0.0F));
                    }

                    Tesselator tesselator = Tesselator.getInstance();
                    BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                    bufferbuilder.addVertex(-1.0F, 1.0F, 0.0F).setUv(0, 0);
                    bufferbuilder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0, 0);
                    bufferbuilder.addVertex(1.0F, -1.0F, 0.0F).setUv(0, 0);
                    bufferbuilder.addVertex(1.0F, 1.0F, 0.0F).setUv(0, 0);
                    BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
                    synthwave.apply();
                    RenderSystem.restoreProjectionMatrix();
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthMask(true);
                }
            }
        }
    }

    public static void startShader() {
        isSynthwaveEnabled = true;
        animationStartTime = System.currentTimeMillis();
        LOGGER.info("Synthwave shader STARTED");
    }

    public static void stopShader() {
        isSynthwaveEnabled = false;
        LOGGER.info("Synthwave shader STOPPED");
    }

    public static boolean isShaderActive() {
        return isSynthwaveEnabled;
    }
}
