package com.buwan.shizuku.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;



import com.buwan.shizuku.compat.ClientTickEvent;
import com.buwan.shizuku.compat.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class VoidPrisonRenderer {
    private static final VoidPrisonRenderer INSTANCE = new VoidPrisonRenderer();
    public static final Map<UUID, PrisonZoneData> activeZones = new ConcurrentHashMap<>();

    public static VoidPrisonRenderer getInstance() {
        return INSTANCE;
    }

    
    public void onClientTick(ClientTickEvent.Post event) {
        activeZones.forEach((uuid, zone) -> zone.tick());
        activeZones.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            if (!activeZones.isEmpty()) {
                Minecraft mc = Minecraft.getInstance();
                Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
                PoseStack poseStack = event.getPoseStack();
                activeZones.forEach((uuid, zone) -> {
                    if (!zone.isExpired()) {
                        this.renderPrisonSphere(poseStack, cameraPos, zone);
                    }
                });
            }
        }
    }

    private void renderPrisonSphere(PoseStack poseStack, Vec3 cameraPos, PrisonZoneData zone) {
        DiagLog.once("prison-draw", "r=%s alpha=%s", zone.radius, zone.getAlpha());
        poseStack.pushPose();
        poseStack.translate(zone.center.x - cameraPos.x, zone.center.y - cameraPos.y, zone.center.z - cameraPos.z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = poseStack.last().pose();
        float baseAlpha = zone.getAlpha() * 0.25F;
        float pulse = zone.getPulse();
        float r = 0.8F * pulse;
        float g = 0.15F;
        float b = 0.15F;
        this.drawSphere(matrix, zone.radius, 30, 20, r, g, b, baseAlpha);
        poseStack.scale(0.95F, 0.95F, 0.95F);
        matrix = poseStack.last().pose();
        this.drawSphere(matrix, zone.radius, 20, 15, r * 1.2F, g * 1.5F, b * 1.5F, baseAlpha * 0.4F);
        poseStack.scale(1.1052631F, 1.1052631F, 1.1052631F);
        matrix = poseStack.last().pose();
        this.drawLatitudeRings(matrix, zone.radius, r * 1.3F, g * 2.0F, b * 2.0F, baseAlpha * 2.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private void drawSphere(Matrix4f matrix, double radius, int segments, int rings, float r, float g, float b, float alpha) {
        // 1.21.1 的 build() 会结束构建状态，因此每一层都必须重新 begin
        BufferBuilder buffer = com.buwan.shizuku.compat.TesselatorCompat.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < rings; i++) {
            double lat0 = Math.PI * (-0.5 + (double) i / (double) rings);
            double lat1 = Math.PI * (-0.5 + (double) (i + 1) / (double) rings);
            double y0 = Math.sin(lat0) * radius;
            double y1 = Math.sin(lat1) * radius;
            double r0 = Math.cos(lat0) * radius;
            double r1 = Math.cos(lat1) * radius;

            for (int j = 0; j < segments; j++) {
                double lng0 = (Math.PI * 2) * (double) j / (double) segments;
                double lng1 = (Math.PI * 2) * (double) (j + 1) / (double) segments;
                double x00 = Math.cos(lng0) * r0;
                double z00 = Math.sin(lng0) * r0;
                double x01 = Math.cos(lng1) * r0;
                double z01 = Math.sin(lng1) * r0;
                double x10 = Math.cos(lng0) * r1;
                double z10 = Math.sin(lng0) * r1;
                double x11 = Math.cos(lng1) * r1;
                double z11 = Math.sin(lng1) * r1;
                float edgeFactor = (float) (1.0 - Math.abs(Math.cos(lat0)));
                float currentAlpha = alpha * (0.6F + edgeFactor * 0.4F);
                buffer.vertex(matrix, (float) x00, (float) y0, (float) z00).color(r, g, b, currentAlpha).endVertex();
                buffer.vertex(matrix, (float) x01, (float) y0, (float) z01).color(r, g, b, currentAlpha).endVertex();
                buffer.vertex(matrix, (float) x10, (float) y1, (float) z10).color(r, g, b, currentAlpha).endVertex();
                buffer.vertex(matrix, (float) x01, (float) y0, (float) z01).color(r, g, b, currentAlpha).endVertex();
                buffer.vertex(matrix, (float) x11, (float) y1, (float) z11).color(r, g, b, currentAlpha).endVertex();
                buffer.vertex(matrix, (float) x10, (float) y1, (float) z10).color(r, g, b, currentAlpha).endVertex();
            }
        }

        BufferUploader.drawWithShader(buffer.end());
    }

    private void drawLatitudeRings(Matrix4f matrix, double radius, float r, float g, float b, float alpha) {
        BufferBuilder lineBuffer = com.buwan.shizuku.compat.TesselatorCompat.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        double[] latitudes = new double[]{-Math.PI * 2.0 / 5.0, -Math.PI / 6, 0.0, Math.PI / 6, Math.PI * 2.0 / 5.0};
        int segments = 60;

        for (double lat : latitudes) {
            double y = Math.sin(lat) * radius;
            double ringRadius = Math.cos(lat) * radius;

            for (int i = 0; i <= segments; i++) {
                double angle = (Math.PI * 2) * (double) i / (double) segments;
                double x = Math.cos(angle) * ringRadius;
                double z = Math.sin(angle) * ringRadius;
                lineBuffer.vertex(matrix, (float) x, (float) y, (float) z).color(r, g, b, alpha).endVertex();
            }
        }

        int meridians = 16;
        int rings = 30;

        for (int m = 0; m < meridians; m++) {
            double lng = (Math.PI * 2) * (double) m / (double) meridians;

            for (int i = 0; i <= rings; i++) {
                double lat = Math.PI * (-0.5 + (double) i / (double) rings);
                double y = Math.sin(lat) * radius;
                double rRing = Math.cos(lat) * radius;
                double x = Math.cos(lng) * rRing;
                double z = Math.sin(lng) * rRing;
                lineBuffer.vertex(matrix, (float) x, (float) y, (float) z).color(r, g, b, alpha * 0.6F).endVertex();
            }
        }

        BufferUploader.drawWithShader(lineBuffer.end());
    }

    public static void addPrisonZone(UUID playerId, Vec3 center, double radius, int duration) {
        activeZones.put(playerId, new PrisonZoneData(center, radius, duration));
        org.slf4j.LoggerFactory.getLogger("shizuku-field").info(
                "[shizuku-field] addPrisonZone {} at {} r={} dur={} total={}",
                playerId, center, radius, duration, activeZones.size());
    }

    public static void removePrisonZone(UUID playerId) {
        activeZones.remove(playerId);
        org.slf4j.LoggerFactory.getLogger("shizuku-field").info(
                "[shizuku-field] removePrisonZone {} total={}", playerId, activeZones.size());
    }

    public static void clearAll() {
        activeZones.clear();
    }

    public static class PrisonZoneData {
        public Vec3 center;
        public double radius;
        public int ticksExisted;
        public int maxTicks;

        public PrisonZoneData(Vec3 center, double radius, int maxTicks) {
            this.center = center;
            this.radius = radius;
            this.ticksExisted = 0;
            this.maxTicks = maxTicks;
        }

        public void tick() {
            this.ticksExisted++;
        }

        public boolean isExpired() {
            return this.ticksExisted >= this.maxTicks;
        }

        public float getAlpha() {
            return this.ticksExisted < 10 ? (float) this.ticksExisted / 10.0F : 1.0F;
        }

        public float getPulse() {
            return (float) (Math.sin((double) this.ticksExisted * 0.15) * 0.2 + 0.8);
        }
    }
}
