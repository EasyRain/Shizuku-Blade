package com.buwan.shizuku.client;

import com.buwan.shizuku.client.shader.CosmicShaders;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;



import com.buwan.shizuku.compat.ClientTickEvent;
import com.buwan.shizuku.compat.RenderGuiEvent;
import com.buwan.shizuku.compat.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class GalaxyFieldRenderer {
    private static final Map<UUID, ZoneData> activeZones = new ConcurrentHashMap<>();

    /** 世界阶段捕获的相机矩阵（供光影下的 GUI 阶段重绘使用） */
    private static Matrix4f worldVertexMatrix;
    private static Matrix4f worldModelView;
    private static Matrix4f worldProjection;
    private static Vec3 worldCameraPos;

    private GalaxyFieldRenderer() {
    }

    public static void addZone(UUID id, Vec3 center, float radius, int duration) {
        activeZones.put(id, new ZoneData(center, radius));
        org.slf4j.LoggerFactory.getLogger("shizuku-field").info(
                "[shizuku-field] addZone {} at {} r={} total={}", id, center, radius, activeZones.size());
    }

    public static void removeZone(UUID id) {
        activeZones.remove(id);
        org.slf4j.LoggerFactory.getLogger("shizuku-field").info(
                "[shizuku-field] removeZone {} total={}", id, activeZones.size());
    }

    
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!activeZones.isEmpty()) {
            for (ZoneData zone : activeZones.values()) {
                zone.ageTicks++;
            }
        }
    }

    
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            // 每帧捕获世界相机矩阵，供光影下的 GUI 阶段重绘复现相同的投影关系
            worldVertexMatrix = new Matrix4f(event.getPoseStack().last().pose());
            worldModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            worldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            worldCameraPos = event.getCamera().getPosition();

            if (!ShaderPackCompat.isShaderPackActive() && !DiagLog.DEBUG_GUI) {
                if (!activeZones.isEmpty()) {
                    if (CosmicShaders.ensureShader() != null) {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.level != null && mc.player != null) {
                            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
                            PoseStack poseStack = event.getPoseStack();
                            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

                            for (Map.Entry<UUID, ZoneData> entry : activeZones.entrySet()) {
                                Player owner = mc.level.getPlayerByUUID(entry.getKey());
                                if (owner != null) {
                                    ZoneData zone = entry.getValue();
                                    poseStack.pushPose();
                                    poseStack.translate(owner.getX() - cam.x, owner.getY() - cam.y, owner.getZ() - cam.z);
                                    drawDisk(buffers, zone, poseStack.last().pose(), false);
                                    poseStack.popPose();
                                }
                            }

                            buffers.endBatch(CosmicShaders.COSMIC_RENDER_TYPE);
                        }
                    }
                }
            }
        }
    }

    /**
     * 光影（Iris/Oculus）下：世界阶段的自定义 RenderType 会被光影管线跳过。
     * 改为在 GUI 阶段（世界已由光影合成进屏幕）用捕获的世界相机矩阵重绘圆盘。
     * 代价：屏幕帧缓冲无深度 —— 圆盘不会被地形遮挡（对半透明星空领域几乎无感知）。
     */
    
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (!ShaderPackCompat.isShaderPackActive() && !DiagLog.DEBUG_GUI) {
            return;
        }
        if (activeZones.isEmpty() || CosmicShaders.ensureShader() == null) {
            return;
        }
        if (worldVertexMatrix == null || worldModelView == null || worldProjection == null || worldCameraPos == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        DiagLog.once("galaxy-gui", "zones=%s cam=%s", activeZones.size(), worldCameraPos);

        RenderSystem.backupProjectionMatrix();
        com.mojang.blaze3d.vertex.PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.last().pose().set(worldModelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(worldProjection, VertexSorting.DISTANCE_TO_ORIGIN);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        for (Map.Entry<UUID, ZoneData> entry : activeZones.entrySet()) {
            Player owner = mc.level.getPlayerByUUID(entry.getKey());
            if (owner != null) {
                ZoneData zone = entry.getValue();
                Matrix4f mat = new Matrix4f(worldVertexMatrix)
                        .translate((float) (owner.getX() - worldCameraPos.x),
                                (float) (owner.getY() - worldCameraPos.y),
                                (float) (owner.getZ() - worldCameraPos.z));
                drawDisk(buffers, zone, mat, true);
            }
        }

        buffers.endBatch(CosmicShaders.COSMIC_RENDER_TYPE_NO_DEPTH);

        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    private static void drawDisk(MultiBufferSource.BufferSource buffers, ZoneData zone, Matrix4f mat, boolean noDepth) {
        float alpha = Math.min(1.0F, (float) zone.ageTicks / 4.0F) * 0.75F;
        if (!(alpha <= 0.01F)) {
            // 光影 GUI 阶段沿用玩家视角（世界特效朝向），不冻结 yaw/pitch
            boolean prepared = CosmicShaders.prepareCosmicRender(alpha, false);
            DiagLog.once("galaxy-draw", "age=%s alpha=%s prepared=%s", zone.ageTicks, alpha, prepared);
            if (prepared) {
                VertexConsumer vb = buffers.getBuffer(noDepth
                        ? CosmicShaders.COSMIC_RENDER_TYPE_NO_DEPTH
                        : CosmicShaders.COSMIC_RENDER_TYPE);
                float radius = zone.radius;
                float y = 0.05F;
                int segments = 48;

                for (int i = 0; i < segments; i++) {
                    double a0 = (Math.PI * 2) * (double) i / (double) segments;
                    double a1 = (Math.PI * 2) * (double) (i + 1) / (double) segments;
                    float x0 = (float) (Math.cos(a0) * (double) radius);
                    float z0 = (float) (Math.sin(a0) * (double) radius);
                    float x1 = (float) (Math.cos(a1) * (double) radius);
                    float z1 = (float) (Math.sin(a1) * (double) radius);
                    vb.vertex(mat, x0, y, z0)
                            .color(1.0F, 1.0F, 1.0F, alpha)
                            .uv(0.0F, 0.0F)
                            .overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(15728880)
                            .normal(0.0F, 1.0F, 0.0F).endVertex();
                    vb.vertex(mat, x1, y, z1)
                            .color(1.0F, 1.0F, 1.0F, alpha)
                            .uv(0.0F, 0.0F)
                            .overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(15728880)
                            .normal(0.0F, 1.0F, 0.0F).endVertex();
                    vb.vertex(mat, 0.0F, y, 0.0F)
                            .color(1.0F, 1.0F, 1.0F, alpha)
                            .uv(0.0F, 0.0F)
                            .overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(15728880)
                            .normal(0.0F, 1.0F, 0.0F).endVertex();
                }
            }
        }
    }

    public static class ZoneData {
        public final double x;
        public final double y;
        public final double z;
        public final float radius;
        public int ageTicks;

        public ZoneData(Vec3 center, float radius) {
            this.x = center.x;
            this.y = center.y;
            this.z = center.z;
            this.radius = radius;
        }
    }
}
