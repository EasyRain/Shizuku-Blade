package com.buwan.shizuku.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;

/**
 * NeoForge {@code RenderLevelStageEvent} 的兼容替身。
 * Fabric 用 WorldRenderEvents 派发：AFTER_ENTITIES → AFTER_CUTOUT_BLOCKS、AFTER_TRANSLUCENT → AFTER_TRANSLUCENT_BLOCKS。
 */
public final class RenderLevelStageEvent {
    public enum Stage {
        AFTER_CUTOUT_BLOCKS,
        AFTER_TRANSLUCENT_BLOCKS
    }

    private final PoseStack poseStack;
    private final Camera camera;
    private final Stage stage;
    private final Matrix4f projectionMatrix;

    public RenderLevelStageEvent(PoseStack poseStack, Camera camera, Stage stage) {
        this(poseStack, camera, stage, null);
    }

    public RenderLevelStageEvent(PoseStack poseStack, Camera camera, Stage stage, Matrix4f projectionMatrix) {
        this.poseStack = poseStack;
        this.camera = camera;
        this.stage = stage;
        this.projectionMatrix = projectionMatrix;
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

    /** 仅 AFTER_CUTOUT_BLOCKS 派发时非 null。 */
    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }
}
