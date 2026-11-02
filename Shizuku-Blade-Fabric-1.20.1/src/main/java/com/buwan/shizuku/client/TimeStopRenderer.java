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


public class TimeStopRenderer {
    private static final TimeStopRenderer INSTANCE = new TimeStopRenderer();
    public static final Map<UUID, TimeStopZoneData> activeZones = new ConcurrentHashMap<>();

    public static TimeStopRenderer getInstance() {
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
                        this.renderTimeStopSphere(poseStack, cameraPos, zone);
                    }
                });
            }
        }
    }

    private void renderTimeStopSphere(PoseStack poseStack, Vec3 cameraPos, TimeStopZoneData zone) {
        DiagLog.once("timestop-draw", "r=%s alpha=%s", zone.radius, zone.getAlpha());
        DiagLog.once("timestop-mat", "cam=%s pose=%s proj=%s",
                cameraPos, DiagLog.fmt(poseStack.last().pose()),
                DiagLog.fmt(RenderSystem.getProjectionMatrix()));
        poseStack.pushPose();
        poseStack.translate(zone.center.x - cameraPos.x, zone.center.y - cameraPos.y, zone.center.z - cameraPos.z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = poseStack.last().pose();
        float baseAlpha = zone.getAlpha() * 0.3F;
        if (DiagLog.DEBUG_BRIGHT) {
            // 诊断：不透明品红大球 + 关深度测试。看不见 => 几何根本没进画面（变换/阶段问题）；
            // 看得见 => 之前只是 alpha 太淡或颜色问题。
            RenderSystem.disableDepthTest();
            this.drawSphere(matrix, zone.radius, 30, 20, 1.0F, 0.0F, 1.0F, 1.0F);
            RenderSystem.enableDepthTest();
        } else {
            this.drawSphere(matrix, zone.radius, 30, 20, 1.0F, 0.84F, 0.0F, baseAlpha);
        }
        poseStack.scale(0.95F, 0.95F, 0.95F);
        matrix = poseStack.last().pose();
        this.drawSphere(matrix, zone.radius, 20, 15, 1.0F, 0.95F, 0.4F, baseAlpha * 0.5F);
        this.drawLatitudeRings(matrix, zone.radius, 1.0F, 1.0F, 0.6F, baseAlpha * 1.5F);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private void drawSphere(Matrix4f matrix, double radius, int segments, int rings, float r, float g, float b, float alpha) {
        // 1.21.1 的 build() 会结束构建状态，因此每一层都必须重新 begin，否则后续 addVertex 抛 "Not building!"
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
                float currentAlpha = alpha * (0.5F + edgeFactor * 0.5F);
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
        double[] latitudes = new double[]{-Math.PI / 3, 0.0, Math.PI / 3};
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

        int meridians = 12;
        int rings = 30;

        for (int m = 0; m < meridians; m++) {
            double lng = (Math.PI * 2) * (double) m / (double) meridians;

            for (int i = 0; i <= rings; i++) {
                double lat = Math.PI * (-0.5 + (double) i / (double) rings);
                double y = Math.sin(lat) * radius;
                double rRing = Math.cos(lat) * radius;
                double x = Math.cos(lng) * rRing;
                double z = Math.sin(lng) * rRing;
                lineBuffer.vertex(matrix, (float) x, (float) y, (float) z).color(r, g, b, alpha * 0.5F).endVertex();
            }
        }

        BufferUploader.drawWithShader(lineBuffer.end());
    }

    public static void addTimeStopZone(UUID playerId, Vec3 center, double radius, int duration) {
        activeZones.put(playerId, new TimeStopZoneData(center, radius, duration));
        org.slf4j.LoggerFactory.getLogger("shizuku-field").info(
                "[shizuku-field] addTimeStopZone {} at {} r={} dur={} total={}",
                playerId, center, radius, duration, activeZones.size());
    }

    public static void removeTimeStopZone(UUID playerId) {
        activeZones.remove(playerId);
        org.slf4j.LoggerFactory.getLogger("shizuku-field").info(
                "[shizuku-field] removeTimeStopZone {} total={}", playerId, activeZones.size());
    }

    public static void clearAll() {
        activeZones.clear();
    }

    public static class TimeStopZoneData {
        public Vec3 center;
        public double radius;
        public int ticksExisted;
        public int maxTicks;

        public TimeStopZoneData(Vec3 center, double radius, int maxTicks) {
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
            if (this.ticksExisted < 10) {
                return (float) this.ticksExisted / 10.0F;
            } else {
                return this.ticksExisted > this.maxTicks - 10 ? (float) (this.maxTicks - this.ticksExisted) / 10.0F : 1.0F;
            }
        }
    }
}
