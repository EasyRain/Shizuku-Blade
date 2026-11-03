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
                Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
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
        poseStack.pushPose();
        poseStack.translate(zone.center.x - cameraPos.x, zone.center.y - cameraPos.y, zone.center.z - cameraPos.z);
        
        
        
        
        
        Matrix4f matrix = poseStack.last().pose();
        float baseAlpha = zone.getAlpha() * 0.3F;
        float pulse = zone.getPulse();
        float r = 0.8F * pulse;
        float g = 0.15F;
        float b = 0.15F;
        this.drawSphere(matrix, zone.radius, 30, 20, r, g, b, baseAlpha);
        poseStack.scale(0.95F, 0.95F, 0.95F);
        matrix = poseStack.last().pose();
        this.drawSphere(matrix, zone.radius, 20, 15, r * 1.2F, g * 1.5F, b * 1.5F, baseAlpha * 0.25F);
        // 网格线框（与原版一致的经纬网观感）
        this.drawGrid(matrix, zone.radius, r * 1.1F, g * 1.2F, b * 1.3F, baseAlpha * 1.6F);
        poseStack.scale(1.1052631F, 1.1052631F, 1.1052631F);
        matrix = poseStack.last().pose();
        
        
        
        poseStack.popPose();
    }

    private void drawSphere(Matrix4f matrix, double radius, int segments, int rings, float r, float g, float b, float alpha) {
        // 1.21.1 的 build() 会结束构建状态，因此每一层都必须重新 begin
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
                float currentAlpha = alpha * (0.6F + edgeFactor * 0.4F);
                // 四边形按逆时针顺序：左下 -> 右下 -> 右上 -> 左上
                buffer.addVertex(matrix, (float) x00, (float) y0, (float) z00).setColor(r, g, b, currentAlpha);
                buffer.addVertex(matrix, (float) x01, (float) y0, (float) z01).setColor(r, g, b, currentAlpha);
                buffer.addVertex(matrix, (float) x11, (float) y1, (float) z11).setColor(r, g, b, currentAlpha);
                buffer.addVertex(matrix, (float) x10, (float) y1, (float) z10).setColor(r, g, b, currentAlpha);
            }
        }

        net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads().draw(buffer.buildOrThrow());
    }


    public static void addPrisonZone(UUID playerId, Vec3 center, double radius, int duration) {
        activeZones.put(playerId, new PrisonZoneData(center, radius, duration));
    }

    public static void removePrisonZone(UUID playerId) {
        activeZones.remove(playerId);
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




    /**
     * 经纬网格（用细四边形带拼出来）。
     *
     * <p>不能用 {@code linesTranslucent()}：26.1 下这条管线与 {@code POSITION_COLOR}
     * 顶点格式不匹配，画线会顶点错位（表现为从球心向外发散的色块），线本身也不可见。
     * 这里改用已验证可用的 {@code debugQuads()}，把每条网格线画成一条很窄的四边形带。</p>
     */
    private void drawGrid(Matrix4f matrix, double radius, float r, float g, float b, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int latCount = 5;
        int segments = 48;
        double latHalf = Math.PI / 90.0;   // 纬度半宽（约 2°）

        // 纬线：每个分段用一条窄带
        for (int li = 1; li < latCount; li++) {
            double lat = Math.PI * (-0.5 + (double) li / (double) latCount);
            for (int i = 0; i < segments; i++) {
                double a0 = (Math.PI * 2) * (double) i / (double) segments;
                double a1 = (Math.PI * 2) * (double) (i + 1) / (double) segments;
                ringQuad(buffer, matrix, radius, lat - latHalf, lat + latHalf, a0, a1, r, g, b, alpha);
            }
        }

        // 经线：沿经度方向的一条窄带
        int merCount = 12;
        int rings = 24;
        double lngHalf = Math.PI / 90.0;
        for (int m = 0; m < merCount; m++) {
            double lng = (Math.PI * 2) * (double) m / (double) merCount;
            for (int i = 0; i < rings; i++) {
                double lat0 = Math.PI * (-0.5 + (double) i / (double) rings);
                double lat1 = Math.PI * (-0.5 + (double) (i + 1) / (double) rings);
                meridianQuad(buffer, matrix, radius, lat0, lat1, lng - lngHalf, lng + lngHalf, r, g, b, alpha);
            }
        }

        net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads().draw(buffer.buildOrThrow());
    }

    /** 纬线窄带：固定两个纬度，横跨 a0..a1 的一个四边形。 */
    private static void ringQuad(BufferBuilder buffer, Matrix4f matrix, double radius,
                                 double latA, double latB, double a0, double a1,
                                 float r, float g, float b, float alpha) {
        double yA = Math.sin(latA) * radius;
        double yB = Math.sin(latB) * radius;
        double rA = Math.cos(latA) * radius;
        double rB = Math.cos(latB) * radius;
        buffer.addVertex(matrix, (float) (Math.cos(a0) * rA), (float) yA, (float) (Math.sin(a0) * rA)).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, (float) (Math.cos(a1) * rA), (float) yA, (float) (Math.sin(a1) * rA)).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, (float) (Math.cos(a1) * rB), (float) yB, (float) (Math.sin(a1) * rB)).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, (float) (Math.cos(a0) * rB), (float) yB, (float) (Math.sin(a0) * rB)).setColor(r, g, b, alpha);
    }

    /** 经线窄带：固定两个经度，沿纬度 lat0..lat1 的一个四边形。 */
    private static void meridianQuad(BufferBuilder buffer, Matrix4f matrix, double radius,
                                     double lat0, double lat1, double lngA, double lngB,
                                     float r, float g, float b, float alpha) {
        double y0 = Math.sin(lat0) * radius;
        double y1 = Math.sin(lat1) * radius;
        double r0 = Math.cos(lat0) * radius;
        double r1 = Math.cos(lat1) * radius;
        buffer.addVertex(matrix, (float) (Math.cos(lngA) * r0), (float) y0, (float) (Math.sin(lngA) * r0)).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, (float) (Math.cos(lngB) * r0), (float) y0, (float) (Math.sin(lngB) * r0)).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, (float) (Math.cos(lngB) * r1), (float) y1, (float) (Math.sin(lngB) * r1)).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, (float) (Math.cos(lngA) * r1), (float) y1, (float) (Math.sin(lngA) * r1)).setColor(r, g, b, alpha);
    }

}
