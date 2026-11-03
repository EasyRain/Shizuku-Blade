package com.buwan.shizuku.client;

import com.buwan.shizuku.client.shader.CosmicShaders;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;

/**
 * 宇宙刀的「手持星光」（26.1，自定义着色器版）。
 *
 * 与 1.20.1 一致：在模型材质**之上**叠一层半透明星空（不替换本体渲染），
 * 这样光影环境下即便自定义着色器不生效，也有本体材质兜底。
 * 时间通过顶点 UV2.x 传入（26.1 的 uniform 需自建 RenderPass 才能绑）。
 */
public final class CosmicBladeRenderer {

    private CosmicBladeRenderer() {
    }

    public static void register() {
        RenderOverrideEvent.CALLBACK.register(CosmicBladeRenderer::onRenderOverride);
    }

    private static void onRenderOverride(RenderOverrideEvent event) {
        String target = event.getTarget();
        if (target == null || !target.endsWith("_luminous")) {
            return;
        }
        WavefrontObject model = event.getModel();
        PoseStack poseStack = event.getPoseStack();
        if (model == null || poseStack == null || event.getTexture() == null) {
            return;
        }
        // 接管 *_luminous 层：本体的发光层需要由我们绘制
        //（着色器会采样刀贴图并按自发光处理，因此夜里也是亮的）。
        event.setCanceled(true);

        int light = event.getPackedLightIn();
        int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        int timeMillis = (int) (System.currentTimeMillis() % 1_000_000_000L);

        // 朝向取自玩家视角，保证星空在世界里保持稳定（原版用的是刀/相机朝向）
        float yawRad = 0.0F;
        float pitchRad = 0.0F;
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            yawRad = (float) Math.toRadians(-player.getYRot());
            pitchRad = (float) Math.toRadians(player.getXRot());
        }
        int yawMilliRad = (int) (yawRad * 1000.0F);
        int pitchMilliRad = (int) (pitchRad * 1000.0F);

        var collector = event.getSubmitNodeCollector();
        if (collector == null) {
            return;
        }
        // 用 submitCustomGeometry 交给渲染管线统一提交；
        // 直接在图层回调里 RenderType.draw(...) 不会真正输出到屏幕。
        collector.submitCustomGeometry(poseStack, CosmicShaders.renderTypeFor(event.getTexture()),
                (pose, consumer) -> model.tessellateAll(
                        new CosmicVertexConsumer(consumer, timeMillis, yawMilliRad, pitchMilliRad),
                        poseStack, light, overlay));
    }

    /** 把 UV2（原版光照图坐标）改写为时间值，供着色器读取；其余属性原样转发。 */
    private static final class CosmicVertexConsumer implements VertexConsumer {

        private final VertexConsumer inner;
        private final int timeMillis;
        private final int yawMilliRad;
        private final int pitchMilliRad;

        private CosmicVertexConsumer(VertexConsumer inner, int timeMillis, int yawMilliRad, int pitchMilliRad) {
            this.inner = inner;
            this.timeMillis = timeMillis;
            this.yawMilliRad = yawMilliRad;
            this.pitchMilliRad = pitchMilliRad;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            inner.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            inner.setColor(color);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            // 原版这里是 overlay 坐标；我们用来传朝向（弧度 * 1000）
            inner.setUv1(yawMilliRad, pitchMilliRad);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            // 原版这里是光照图坐标；我们用来传 时间(ms) 与 不透明度(*1000)
            inner.setUv2(timeMillis, 1000);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            inner.setLineWidth(width);
            return this;
        }
    }
}
