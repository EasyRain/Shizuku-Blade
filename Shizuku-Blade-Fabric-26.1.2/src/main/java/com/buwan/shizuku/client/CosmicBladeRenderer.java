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
        // 只处理「宇宙刀」：其它刀同样带 *_luminous 分组，若不加这个判定，
        // 所有刀都会套上这层着色器（会把非宇宙刀也渲染成星空）。
        var texture = event.getTexture();
        if (target == null || texture == null || !texture.getPath().contains("cosmic")) {
            return;
        }

        // 注意顺序：刀鞘的 target 是 "sheath"（不带 _luminous 后缀），
        // 必须先于下面的 _luminous 判断，否则会被提前 return 掉、前置照样画碎刀鞘。
        if (target.startsWith("sheath")) {
            event.setCanceled(true);
            return;
        }

        if (!target.endsWith("_luminous")) {
            return;
        }
        WavefrontObject model = event.getModel();
        PoseStack poseStack = event.getPoseStack();
        if (model == null || poseStack == null || event.getTexture() == null) {
            return;
        }
        // 不取消本体渲染：取消会让前置退回平面 fallback（表现为物品栏模型叠在手持模型上）。
        // 我们的层叠在材质之上，负责夜间自发光。

        // 前置官方逻辑：isCanceled() 时它这一层直接 return（不会 fallback），
        // 因此我们接管这一层，自己提交几何。
        event.setCanceled(true);

        int light = event.getPackedLightIn();
        int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        int timeMillis = (int) (System.currentTimeMillis() % 1_000_000_000L);

        float yawRad = 0.0F;
        float pitchRad = 0.0F;
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            yawRad = (float) Math.toRadians(-player.getYRot());
            pitchRad = (float) Math.toRadians(player.getXRot());
        }
        int yawMilliRad = (int) (yawRad * 1000.0F);
        int pitchMilliRad = (int) (pitchRad * 1000.0F);

        // 发光层对应「刀身」那一份几何：前置用 "blade_luminous" 触发事件，
        // 而模型里只有 "blade"/"blade_damaged"，所以去掉后缀再按实际存在的分组渲染。
        String group = target.endsWith("_luminous")
                ? target.substring(0, target.length() - "_luminous".length())
                : target;
        if (!hasGroup(model, group)) {
            group = hasGroup(model, "blade") ? "blade" : null;
        }
        if (group == null) {
            return;
        }
        String finalGroup = group;

        var collector = event.getSubmitNodeCollector();
        if (collector == null) {
            return;
        }
        collector.submitCustomGeometry(poseStack, CosmicShaders.renderTypeFor(event.getTexture()),
                (pose, consumer) -> {
                    PoseStack local = new PoseStack();
                    local.last().pose().set(pose.pose());
                    local.last().normal().set(pose.normal());
                    // 覆盖整把刀，但排除两类不该出现的东西：
                    //   sheath*  —— 宇宙刀没有刀鞘，画出来会多一截
                    //   item_*   —— 物品栏用的平面图标（会随视角闪、叠在手持模型上）
                    model.tessellateAllExcept(
                            new CosmicVertexConsumer(consumer, timeMillis, yawMilliRad, pitchMilliRad),
                            local, light, overlay,
                            "sheath", "sheath_luminous",
                            "item_blade", "item_damaged", "item_bladens");
                });
    }

    /** 模型里是否存在该分组（OBJ 的分组名列表是公开的）。 */
    private static boolean hasGroup(WavefrontObject model, String name) {
        if (name == null || model.groupObjects == null) {
            return false;
        }
        for (var group : model.groupObjects) {
            if (name.equals(group.name)) {
                return true;
            }
        }
        return false;
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
