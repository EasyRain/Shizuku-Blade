package com.buwan.shizuku.client;

import com.buwan.shizuku.client.shader.CosmicShaders;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * 宇宙刀的「手持星光」（26.1，1.20.1 原版忠实移植）。
 *
 * <p>原版逻辑（modrinth37 CosmicBladeRenderer + CosmicShaders.renderCosmic）：</p>
 * <ul>
 *   <li>只处理宇宙刀（26.1 事件没有 getStack，用模型贴图名 cosmic.png 等价判定）；</li>
 *   <li>在 {@code *_luminous} 分组事件上，把<b>基础分组</b>（去掉 _luminous 后缀）用
 *       同一个 cosmic RenderType（= 星河圆盘那套：items 图集 + 闪电混合 + 星空 fsh）
 *       tessellate 进去，opacity=1.0、满亮度 15728880；</li>
 *   <li><b>不取消</b>发光层 —— 原版就是「本体材质 + 发光层 + 星空叠加」三层共存；</li>
 *   <li>参数取原版 prepareCosmicRender 非 GUI 路径的值：time=世界刻、yaw=+yRot 弧度、
 *       pitch=-xRot 弧度、externalScale=1.0。</li>
 * </ul>
 *
 * <p>26.1 无 per-draw uniform ⇒ 参数由 {@link CosmicParamsConsumer} 写进着色器不读的
 * 顶点属性槽位（UV0=(time,yaw)、Normal=(pitch,externalScale,0)、Color.a=opacity），
 * 与圆盘（GalaxyFieldRenderer）共用同一管线。</p>
 */
public final class CosmicBladeRenderer {

    /** 手持星光开关（26.1 忠实移植完成后开启）。 */
    public static final boolean ENABLE_COSMIC_HELD_EFFECT = true;

    private CosmicBladeRenderer() {
    }

    public static void register() {
        RenderOverrideEvent.CALLBACK.register(CosmicBladeRenderer::onRenderOverride);
    }

    private static void onRenderOverride(RenderOverrideEvent event) {
        if (!ENABLE_COSMIC_HELD_EFFECT) {
            return;
        }
        String target = event.getTarget();
        if (target == null || !target.endsWith("_luminous")) {
            return;
        }
        // 只处理「宇宙刀」：26.1 事件没有 getStack（渲染走 render state），
        // 宇宙刀的模型贴图 = model/shizuku/cosmic.png，按贴图名等价判定。
        var texture = event.getTexture();
        if (texture == null || !texture.getPath().contains("cosmic")) {
            return;
        }
        WavefrontObject model = event.getModel();
        PoseStack poseStack = event.getPoseStack();
        var collector = event.getSubmitNodeCollector();
        if (model == null || poseStack == null || collector == null) {
            return;
        }
        RenderType renderType = CosmicShaders.cosmicRenderType();
        if (renderType == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        // 原版 prepareCosmicRender 非 GUI 路径的取值
        float time = mc.level.getGameTime() % 2147483647L;
        float yaw = (float) (mc.player.getYRot() * 2.0 * Math.PI / 360.0);
        float pitch = -((float) (mc.player.getXRot() * 2.0 * Math.PI / 360.0));

        // 26.1 Resharped 模型（wdq.obj）把 1.20.1 单一 blade 组拆成了多个组：
        // blade(139面)/blade_damaged(102)/blade_fragment(37)/effect(240 外壳)/handle(92)/item_*
        // —— 忠实等价 = 画「可见刀身」= 事件对应的基础分组（blade 或 blade_damaged）+ handle 柄。
        // 不用 effect 外壳（240 面的鞘状松散包壳，实测画出来是「破碎三角拼成的鞘」）；
        // sheath_luminous 事件自然跳过（该模型没有 sheath 组）。
        String baseTarget = target.substring(0, target.length() - "_luminous".length());
        java.util.List<String> groups = new java.util.ArrayList<>();
        if (hasGroup(model, baseTarget)) {
            groups.add(baseTarget);
        }
        if (hasGroup(model, "handle")) {
            groups.add("handle");
        }
        if (groups.isEmpty()) {
            return;
        }

        // 前置的 submitCustomGeometry 必须用回调给的 pose（否则整层铺满屏，历史踩坑）
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            // ⚠️ Face 的静态算子是全局可变状态：SlashEffectRenderer 等渲染路径会设置
            // alphaOverrideYZZ（按 Y/Z 空间改 alpha），残留时我们的 tessellation 会继承它
            // → 部分面 alpha=0 隐形 = 细长三角形空缺。1.20.1 原版 renderCosmicLegacy 里那套
            // setCol/setLightMap/reset* 反射调用就是干这个中和的（26.1 继任 = Face 的静态字段）。
            java.util.function.BiFunction<org.joml.Vector4f, Integer, Integer> prevAlpha =
                    mods.flammpfeil.slashblade.client.renderer.model.obj.Face.alphaOverride;
            org.joml.Vector4f prevUv =
                    mods.flammpfeil.slashblade.client.renderer.model.obj.Face.uvOperator;
            mods.flammpfeil.slashblade.client.renderer.model.obj.Face.alphaOverride =
                    mods.flammpfeil.slashblade.client.renderer.model.obj.Face.alphaNoOverride;
            mods.flammpfeil.slashblade.client.renderer.model.obj.Face.uvOperator =
                    mods.flammpfeil.slashblade.client.renderer.model.obj.Face.uvDefaultOperator;
            try {
                PoseStack local = new PoseStack();
                local.last().pose().set(pose.pose());
                local.last().normal().set(pose.normal());
                model.tessellateOnly(new CosmicParamsConsumer(consumer, time, yaw, pitch),
                        local, 15728880, 0, groups.toArray(new String[0]));
            } finally {
                mods.flammpfeil.slashblade.client.renderer.model.obj.Face.alphaOverride = prevAlpha;
                mods.flammpfeil.slashblade.client.renderer.model.obj.Face.uvOperator = prevUv;
            }
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

    /**
     * 参数通道 wrapper：把宇宙刀参数写进着色器不读的顶点属性
     * （UV0=(time,yaw)、Normal=(pitch,externalScale,0)、Color.a=opacity=1.0），
     * 其余属性原样转发。与圆盘共用同一个 cosmic 管线。
     */
    private static final class CosmicParamsConsumer implements VertexConsumer {

        private final VertexConsumer inner;
        private final float time;
        private final float yaw;
        private final float pitch;

        private CosmicParamsConsumer(VertexConsumer inner, float time, float yaw, float pitch) {
            this.inner = inner;
            this.time = time;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            // opacity 恒 1.0（原版 renderCosmic 传 1.0F）
            inner.setColor(r, g, b, 255);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            inner.setColor((color & 0x00FFFFFF) | 0xFF000000);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            // 模型 UV 被着色器忽略（原版也不采样刀身贴图），此槽位改传 (time, yaw)
            inner.setUv(time, yaw);
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
            // 模型法线被着色器忽略，此槽位改传 (pitch, externalScale=1.0, 0)
            inner.setNormal(pitch, 1.0F, 0.0F);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            inner.setLineWidth(width);
            return this;
        }
    }
}
