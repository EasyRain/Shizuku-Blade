package com.buwan.shizuku.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;

/**
 * NeoForge {@code RenderLevelStageEvent} 的兼容替身。
 * Fabric 用 WorldRenderEvents.AFTER_TRANSLUCENT 派发，Stage 固定为 AFTER_TRANSLUCENT_BLOCKS。
 */
public final class RenderLevelStageEvent {
    public enum Stage {
        AFTER_TRANSLUCENT_BLOCKS
    }

    private final PoseStack poseStack;
    private final Camera camera;
    private final Stage stage;

    public RenderLevelStageEvent(PoseStack poseStack, Camera camera, Stage stage) {
        this.poseStack = poseStack;
        this.camera = camera;
        this.stage = stage;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public Camera getCamera() {
        return camera;
    }

    public Stage getStage() {
        return stage;
    }
}
