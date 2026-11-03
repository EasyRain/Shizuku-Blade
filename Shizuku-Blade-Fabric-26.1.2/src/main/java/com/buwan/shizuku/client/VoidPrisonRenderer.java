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
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class VoidPrisonRenderer {
    private static final VoidPrisonRenderer INSTANCE = new VoidPrisonRenderer();
    public static final Map<UUID, PrisonZoneData> activeZones = new ConcurrentHashMap<>();

    /** 线宽（像素）。原版 1.20.1 是 1px 硬件线；26.1 改为屏幕空间四边形展开，1.0px 光栅化易断续，取 2.0px 保连续。 */
    private static final float LINE_WIDTH = 2.0F;

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
        float baseAlpha = zone.getAlpha() * 0.25F;
        float pulse = zone.getPulse();
        float r = 0.8F * pulse;
        float g = 0.15F;
        float b = 0.15F;
        this.drawSphere(matrix, zone.radius, 30, 20, r, g, b, baseAlpha);
        poseStack.scale(0.95F, 0.95F, 0.95F);
        matrix = poseStack.last().pose();
        this.drawSphere(matrix, zone.radius, 20, 15, r * 1.2F, g * 1.5F, b * 1.5F, baseAlpha * 0.4F);
        // 原版：网格画在约 1.05 倍半径处（0.95 × 1.1052631），颜色 r×1.3/g×2.0/b×2.0、alpha×2.0、经线 ×0.6
        poseStack.scale(1.1052631F, 1.1052631F, 1.1052631F);
        this.drawGrid(poseStack, zone.radius, r * 1.3F, g * 2.0F, b * 2.0F, baseAlpha * 2.0F);
        
        
        
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
     * 经纬网格 —— 26.1 原生细线实现（替代之前的四边形带妥协方案）。
     *
     * <p>26.1 的线管线 {@code RenderTypes.linesTranslucent()}：顶点格式
     * {@code POSITION_COLOR_NORMAL_LINE_WIDTH}、{@code Mode.LINES}（每段两个顶点）。
     * {@code rendertype_lines.vsh} 用 Normal（指向线段另一端的归一化方向）算屏幕方向、
     * 用 LineWidth（像素）把每段向两侧展开成细四边形，{@code gl_VertexID % 2} 决定偏移正负；
     * 半透明混合、无剔除、不写深度，与原版 1.20.1 的 RenderSystem 状态一致。
     * 顶点写法照抄原版 {@code ShapeRenderer#renderShape}（两端点共用同一个归一化方向）。</p>
     *
     * <p>几何与配色 = 原版 1.20.1 作者参数：5 条纬线（-72°/-30°/0°/+30°/+72°）× 60 段；
     * 16 条经线 × 30 段，经线 alpha ×0.6。</p>
     */
    private void drawGrid(PoseStack poseStack, double radius, float r, float g, float b, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
        PoseStack.Pose pose = poseStack.last();

        // 纬线
        double[] latitudes = {-Math.PI * 2.0 / 5.0, -Math.PI / 6, 0.0, Math.PI / 6, Math.PI * 2.0 / 5.0};
        int segments = 60;
        for (double lat : latitudes) {
            double y = Math.sin(lat) * radius;
            double ringRadius = Math.cos(lat) * radius;
            for (int i = 0; i < segments; i++) {
                double a0 = (Math.PI * 2) * (double) i / (double) segments;
                double a1 = (Math.PI * 2) * (double) (i + 1) / (double) segments;
                line(buffer, pose,
                        Math.cos(a0) * ringRadius, y, Math.sin(a0) * ringRadius,
                        Math.cos(a1) * ringRadius, y, Math.sin(a1) * ringRadius,
                        r, g, b, alpha);
            }
        }

        // 经线（alpha ×0.6，与原版一致）
        int meridians = 16;
        int rings = 30;
        for (int m = 0; m < meridians; m++) {
            double lng = (Math.PI * 2) * (double) m / (double) meridians;
            double cl = Math.cos(lng) * radius;
            double sl = Math.sin(lng) * radius;
            for (int i = 0; i < rings; i++) {
                double lat0 = Math.PI * (-0.5 + (double) i / (double) rings);
                double lat1 = Math.PI * (-0.5 + (double) (i + 1) / (double) rings);
                line(buffer, pose,
                        cl * Math.cos(lat0), Math.sin(lat0) * radius, sl * Math.cos(lat0),
                        cl * Math.cos(lat1), Math.sin(lat1) * radius, sl * Math.cos(lat1),
                        r, g, b, alpha * 0.6F);
            }
        }

        net.minecraft.client.renderer.rendertype.RenderTypes.linesTranslucent().draw(buffer.buildOrThrow());
    }

    /** 画一条线段：两个端点共用「指向另一端」的归一化方向作为 Normal（照抄原版 ShapeRenderer 的写法）。 */
    private static void line(BufferBuilder buffer, PoseStack.Pose pose,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             float r, float g, float b, float a) {
        Vector3f dir = new Vector3f((float) (x1 - x0), (float) (y1 - y0), (float) (z1 - z0)).normalize();
        buffer.addVertex(pose, (float) x0, (float) y0, (float) z0)
                .setColor(r, g, b, a)
                .setNormal(pose, dir)
                .setLineWidth(LINE_WIDTH);
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(r, g, b, a)
                .setNormal(pose, dir)
                .setLineWidth(LINE_WIDTH);
    }

}
