package com.buwan.shizuku.client;

import com.buwan.shizuku.client.shader.CosmicShaders;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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
    }

    public static void removeZone(UUID id) {
        activeZones.remove(id);
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
            worldProjection = null;
            worldCameraPos = event.getCamera().position();

            if (!ShaderPackCompat.isShaderPackActive()) {
                if (!activeZones.isEmpty()) {
                    // 26.1 不再有 ShaderInstance（COSMIC_SHADER 恒为 null），
                    // 这里直接用 RenderType 绘制，不能再用它当条件。
                    {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.level != null && mc.player != null) {
                            Vec3 cam = mc.gameRenderer.getMainCamera().position();
                            PoseStack poseStack = event.getPoseStack();
                            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

                            for (Map.Entry<UUID, ZoneData> entry : activeZones.entrySet()) {
                                Player owner = mc.level.getPlayerByUUID(entry.getKey());
                                if (owner != null) {
                                    ZoneData zone = entry.getValue();
                                    poseStack.pushPose();
                                    poseStack.translate(owner.getX() - cam.x, owner.getY() - cam.y, owner.getZ() - cam.z);
                                    drawDisk(zone, poseStack.last().pose());
                                    poseStack.popPose();
                                }
                            }

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
        if (!ShaderPackCompat.isShaderPackActive()) {
            return;
        }
        if (activeZones.isEmpty()) {
            return;
        }
        if (worldVertexMatrix == null || worldModelView == null || worldCameraPos == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        for (Map.Entry<UUID, ZoneData> entry : activeZones.entrySet()) {
            Player owner = mc.level.getPlayerByUUID(entry.getKey());
            if (owner != null) {
                ZoneData zone = entry.getValue();
                Matrix4f mat = new Matrix4f(worldVertexMatrix)
                        .translate((float) (owner.getX() - worldCameraPos.x),
                                (float) (owner.getY() - worldCameraPos.y),
                                (float) (owner.getZ() - worldCameraPos.z));
                drawDisk(zone, mat);
            }
        }

    }

    /** 星空贴图：26.1 没有自定义星空着色器，改用贴图 + 径向 UV 还原星盘观感。 */
    private static final net.minecraft.resources.Identifier STARRY_TEXTURE =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("shizuku", "textures/entity/starry_sky.png");

    private static void drawDisk(ZoneData zone, Matrix4f mat) {
        float alpha = Math.min(1.0F, (float) zone.ageTicks / 4.0F) * 0.75F;
        if (alpha <= 0.01F) {
            return;
        }
        float radius = zone.radius;
        float y = 0.05F;
        int segments = 96;
        BufferBuilder vb = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.ENTITY);
        for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2) * (double) i / (double) segments;
            double a1 = (Math.PI * 2) * (double) (i + 1) / (double) segments;
            float x0 = (float) (Math.cos(a0) * (double) radius);
            float z0 = (float) (Math.sin(a0) * (double) radius);
            float x1 = (float) (Math.cos(a1) * (double) radius);
            float z1 = (float) (Math.sin(a1) * (double) radius);
            // 圆心 UV 为贴图中心；边缘 UV 沿圆周展开，形成放射状星空
            float u0 = (float) (0.5 + Math.cos(a0) * 0.5);
            float v0 = (float) (0.5 + Math.sin(a0) * 0.5);
            float u1 = (float) (0.5 + Math.cos(a1) * 0.5);
            float v1 = (float) (0.5 + Math.sin(a1) * 0.5);
            // 边缘淡出：圆心不透明，外缘 alpha 归零，避免生硬的圆边
            vertex(vb, mat, 0.0F, y, 0.0F, 0.5F, 0.5F, alpha);
            vertex(vb, mat, x0, y, z0, u0, v0, 0.0F);
            vertex(vb, mat, x1, y, z1, u1, v1, 0.0F);
        }
        net.minecraft.client.renderer.rendertype.RenderTypes
                .entityTranslucent(STARRY_TEXTURE)
                .draw(vb.buildOrThrow());
    }

    private static void vertex(BufferBuilder vb, Matrix4f mat, float x, float y, float z,
                               float u, float v, float alpha) {
        vb.addVertex(mat, x, y, z)
                .setColor(1.0F, 1.0F, 1.0F, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(0.0F, 1.0F, 0.0F);
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
