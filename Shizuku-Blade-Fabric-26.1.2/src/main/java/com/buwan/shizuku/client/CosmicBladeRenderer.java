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
 * 用 CosmicShaders 里的自定义管线和着色器一次性画出「刀身 + 流动星空」：
 * - 取消本体渲染，避免画两遍；
 * - 自己 tessellate 模型，写顶点时把时间塞进 UV2.x
 *   （26.1 的 uniform 要自建 RenderPass 才能绑，走顶点属性可以绕开）；
 * - 用宇宙刀 RenderType 画出结果。
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
        event.setCanceled(true);

        int light = event.getPackedLightIn();
        int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        int timeMillis = (int) (System.currentTimeMillis() % 1_000_000_000L);

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.ENTITY);
        model.tessellateAll(new CosmicVertexConsumer(buffer, timeMillis), poseStack, light, overlay);
        var mesh = buffer.build();
        if (mesh != null) {
            CosmicShaders.renderTypeFor(event.getTexture()).draw(mesh);
        }
    }

    /** 把 UV2（原版光照图坐标）改写为时间值，供着色器读取；其余属性原样转发。 */
    private static final class CosmicVertexConsumer implements VertexConsumer {

        private final VertexConsumer inner;
        private final int timeMillis;

        private CosmicVertexConsumer(VertexConsumer inner, int timeMillis) {
            this.inner = inner;
            this.timeMillis = timeMillis;
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
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            inner.setUv2(timeMillis, 0);
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
