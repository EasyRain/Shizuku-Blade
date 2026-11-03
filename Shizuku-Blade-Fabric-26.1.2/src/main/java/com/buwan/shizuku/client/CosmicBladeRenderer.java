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
        // 只处理「宇宙刀」：其它刀同样带 *_luminous 分组，若不加这个判定，
        // 所有刀都会套上这层着色器（会把非宇宙刀也渲染成星空）。
        var texture = event.getTexture();
        if (texture == null || !texture.getPath().contains("cosmic")) {
            return;
        }
        WavefrontObject model = event.getModel();
        PoseStack poseStack = event.getPoseStack();
        if (model == null || poseStack == null || event.getTexture() == null) {
            return;
        }
        // 不取消本体渲染：取消会让前置退回平面 fallback（表现为物品栏模型叠在手持模型上）。
        // 我们的层叠在材质之上，负责夜间自发光。

        // 只替换渲染类型，让前置自己画模型。
        // 之前用 submitCustomGeometry 额外提交了一遍几何，结果物品栏的平面图标也叠到了
        // 手持模型上（那层是前置自己画的），改成替换 RenderType 最轻、几何也最正确。
        event.setGetRenderType(CosmicShaders::renderTypeFor);
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
