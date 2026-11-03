package com.buwan.shizuku.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
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
                Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
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
        poseStack.pushPose();
        poseStack.translate(zone.center.x - cameraPos.x, zone.center.y - cameraPos.y, zone.center.z - cameraPos.z);
        
        
        
        
        
        Matrix4f matrix = poseStack.last().pose();
        float baseAlpha = zone.getAlpha() * 0.12F;
        this.drawSphere(matrix, zone.radius, 30, 20, 1.0F, 0.84F, 0.0F, baseAlpha);
        poseStack.scale(0.95F, 0.95F, 0.95F);
        matrix = poseStack.last().pose();
        this.drawSphere(matrix, zone.radius, 20, 15, 1.0F, 0.95F, 0.4F, baseAlpha * 0.25F);
        
        
        
        poseStack.popPose();
    }

    private void drawSphere(Matrix4f matrix, double radius, int segments, int rings, float r, float g, float b, float alpha) {
        // 1.21.1 的 build() 会结束构建状态，因此每一层都必须重新 begin，否则后续 addVertex 抛 "Not building!"
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
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
                // 纬度环纹：用 alpha 调制模拟原本的线框环（26.1 的 linesTranslucent
                // 顶点格式与 POSITION_COLOR 不匹配，画线会产生乱飞的色块）
                float band = 0.72F + 0.28F * (float) Math.cos(lat0 * 8.0);
                float currentAlpha = alpha * (0.5F + edgeFactor * 0.5F) * band;
                // 四边形按逆时针顺序：左下 -> 右下 -> 右上 -> 左上
                buffer.addVertex(matrix, (float) x00, (float) y0, (float) z00).setColor(r, g, b, currentAlpha).setUv(0, 0);
                buffer.addVertex(matrix, (float) x01, (float) y0, (float) z01).setColor(r, g, b, currentAlpha).setUv(0, 0);
                buffer.addVertex(matrix, (float) x11, (float) y1, (float) z11).setColor(r, g, b, currentAlpha).setUv(0, 0);
                buffer.addVertex(matrix, (float) x10, (float) y1, (float) z10).setColor(r, g, b, currentAlpha).setUv(0, 0);
            }
        }

        net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads().draw(buffer.buildOrThrow());
    }


    public static void addTimeStopZone(UUID playerId, Vec3 center, double radius, int duration) {
        activeZones.put(playerId, new TimeStopZoneData(center, radius, duration));
    }

    public static void removeTimeStopZone(UUID playerId) {
        activeZones.remove(playerId);
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
