package com.buwan.shizuku.client;

import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * 宇宙刀的「手持星光」覆盖层（26.1）。
 *
 * <p>1.20.1/1.21.1 用的是自定义星空着色器；26.1 的自定义着色器要走
 * RenderPipeline + RenderPass 自行绑定 uniform，成本很高。这里换个思路：<b>不用着色器</b>——
 * 模型由我们自己 tessellate，写顶点时用 {@link UvScrollConsumer} 把 UV 按时间平推，
 * 星空贴图就会顺着刀身流动，观感接近原来的扫光效果。</p>
 *
 * <p>事件 {@link RenderOverrideEvent} 在 26.1 依然可用且可取消：我们取消本体渲染后，
 * 先用本体自己的 RenderType 画一遍原模型，再叠一层流动星空。</p>
 */
public final class CosmicBladeRenderer {

    /** 星空贴图（与星河领域圆盘共用同一张）。 */
    private static final Identifier STARRY_TEXTURE =
            shizuku.prefix("textures/entity/starry_sky.png");

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
        if (event.isCanceled()) {
            return;
        }
        WavefrontObject model = event.getModel();
        if (model == null) {
            return;
        }
        event.setCanceled(true);

        PoseStack poseStack = event.getPoseStack();
        int light = event.getPackedLightIn();
        int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

        // 第一遍：用本体自己的贴图 + RenderType 画出刀身，保证外观不变
        BufferBuilder base = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.ENTITY);
        model.tessellateOnly(base, poseStack, light, overlay, target);
        RenderType baseType = event.getGetRenderType() != null
                ? event.getGetRenderType().apply(event.getTexture())
                : RenderTypes.entityTranslucent(event.getTexture());
        baseType.draw(base.buildOrThrow());

        // 第二遍：叠加流动星空（UV 随时间平推）
        float scroll = (System.currentTimeMillis() % 6000L) / 6000.0F;
        BufferBuilder stars = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.ENTITY);
        model.tessellateOnly(new UvScrollConsumer(stars, scroll, 0.0F),
                poseStack, light, overlay, target);
        RenderTypes.entityTranslucent(STARRY_TEXTURE).draw(stars.buildOrThrow());
    }

    /**
     * 把 {@code setUv(u, v)} 的 u 加上滚动偏移的装饰器（其余调用原样转发）。
     * 贴图按 repeat 采样，UV 越界不会出问题。
     */
    private static final class UvScrollConsumer implements VertexConsumer {

        private final VertexConsumer inner;
        private final float offsetU;
        private final float offsetV;

        private UvScrollConsumer(VertexConsumer inner, float offsetU, float offsetV) {
            this.inner = inner;
            this.offsetU = offsetU;
            this.offsetV = offsetV;
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
            inner.setUv(u + offsetU, v + offsetV);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            inner.setUv2(u, v);
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
