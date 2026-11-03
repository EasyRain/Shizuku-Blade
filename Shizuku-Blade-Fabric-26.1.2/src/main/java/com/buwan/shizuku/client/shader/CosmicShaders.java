package com.buwan.shizuku.client.shader;

import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 宇宙刀/星河领域的自定义着色器（26.1，1.20.1 原版忠实移植）。
 *
 * <p>原版结构：Sampler0 = 方块图集（10 张 {@code item/misc/cosmic_N} 星点精灵缝进
 * {@code assets/shizuku/atlases/blocks.json}），cosmic.fsh 的 16 层随机旋转射线按
 * {@code cosmicuvs[10]}（每精灵 uv 矩形）采样图集画星空。</p>
 *
 * <p>26.1 差异（只有数据通道，数学与原版完全一致）：</p>
 * <ul>
 *   <li>管线 = {@code RenderPipeline.builder(ENTITY_SNIPPET)} + 自定义 vsh/fsh + 无剔除；</li>
 *   <li>原版每帧 uniform（time/yaw/pitch/externalScale/opacity）→ 顶点属性
 *      （UV0=(time,yaw)、Normal=(pitch,externalScale,0)、Color.a=opacity，都是原版 shader
 *       本来就不读的槽位）；</li>
 *   <li>{@code cosmicuvs[10]}（mat2 数组 uniform）→ 40 个 {@code withShaderDefine} 浮点常量
 *       （全精度；图集重缝时 uvs 变化 → 自动重建管线）。</li>
 * </ul>
 */
public final class CosmicShaders {

    private static final Logger LOGGER = LoggerFactory.getLogger(CosmicShaders.class);

    /** 「宇宙刀」对应的刀翻译键（判定哪些刀需要星光效果）。 */
    public static final String COSMIC_BLADE_KEY = "item.shizuku.mosmicblade";

    /** 满亮度（= 1.20.1 原版 renderCosmic 里写死的 15728880）。 */
    public static final int MAX_LIGHT = 15728880;

    /**
     * 物品图标专用的 externalScale（世界的 cosmic 参数是 1.0，1.20.1 的 inventoryRender 预设是 100）。
     * 图标在 GUI 正交投影下片段方向几乎没有变化，必须靠它放大采样密度：
     * 1 ⇒ 条纹（放太大）、100 ⇒ 噪点（放太小），16 ⇒ 横跨约 2~6 个星点瓦片（能看出星点 + 星云）。
     * 观感想更细就调大、更粗就调小，只影响图标。
     */
    public static final float ICON_EXTERNAL_SCALE = 16.0F;

    public static final Identifier SHADER_ID = shizuku.prefix("core/cosmic");
    public static final Identifier PIPELINE_ID = shizuku.prefix("pipeline/cosmic");

    /** 与原版同序：[i*4]=U0, +1=V0, +2=U1, +3=V1（对应 fsh mat2(U0,V0,U1,V1) 的列主序）。 */
    public static final float[] COSMIC_UVS = new float[40];

    private static RenderPipeline pipeline;
    private static RenderType cosmicRenderType;
    /** 物品图标专用（与 {@link #cosmicRenderType} 同管线，只是不带 layering transform）。 */
    private static RenderType cosmicIconRenderType;
    private static boolean spritesWarned;

    /** AtlasManager 的 atlasById 键 = 图集 json id（= AtlasIds.ITEMS）。⚠️ 26.1 把 item/ 目录
     *  的自动缝合从 blocks 图集挪到了独立 items 图集（vanilla items.json = directory source item/），
     *  textures/item/misc/cosmic_N 天然在 items 图集里；1.20.1 的「BLOCK_ATLAS 含 item 精灵」已过时。 */
    private static final Identifier ITEMS_ATLAS_ID = Identifier.fromNamespaceAndPath("minecraft", "items");

    private CosmicShaders() {
    }

    /** 宇宙星空 RenderType（Sampler0 = items 图集；圆盘与刀身共用，同原版 COSMIC_RENDER_TYPE）。 */
    public static RenderType cosmicRenderType() {
        return ensureRenderTypes() ? cosmicRenderType : null;
    }

    /** 物品图标专用的星空 RenderType（同管线、无 Z 层叠偏移，见 {@code rebuild()} 注释）。 */
    public static RenderType cosmicIconRenderType() {
        return ensureRenderTypes() ? cosmicIconRenderType : null;
    }

    private static boolean ensureRenderTypes() {
        if (!collectSprites()) {
            if (!spritesWarned) {
                spritesWarned = true;
                LOGGER.warn("Cosmic sprites missing from items atlas; all 10 rects identical (missing-sprite fallback)? first rect u0={} v0={} u1={} v1={}",
                        COSMIC_UVS[0], COSMIC_UVS[1], COSMIC_UVS[2], COSMIC_UVS[3]);
            }
            return false;
        }
        if (cosmicRenderType == null || cosmicIconRenderType == null) {
            rebuild();
        }
        return true;
    }

    /** 兼容旧调用（刀身路径，当前休眠）：原版刀身也用同一个图集 RenderType，不采样本体贴图。 */
    public static RenderType renderTypeFor(Identifier texture) {
        return cosmicRenderType();
    }

    /** 模型单分组星空叠层：勾选成功返回 true（着色器/精灵/玩家都就绪）。 */
    public static boolean submitModel(WavefrontObject model, String group, PoseStack poseStack,
                                      SubmitNodeCollector collector, float opacity) {
        if (model == null || group == null || poseStack == null || collector == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return false;
        }
        // ⚠️ 物品栏/快捷栏图标也走同一个事件！上游 {@code SlashBladeTEISR.renderIcon} 把分组名
        // 拼上 "_luminous" 再发事件（字节码里是 makeConcatWithConstants(item_blade + "_luminous")），
        // 所以「item_blade_luminous」会被这里接住、把星空层叠到图标上。
        // 图标采样密度单独标定（实机三个数据点定出来的）：Δu = Δrawu * scale * externalScale，
        // 图标在 GUI 正交下 Δdir 极小（几像素 / 1000）：
        //   externalScale=1   ⇒ 只占一块星点精灵的一小块 ⇒ 16 层随机旋转把它拉成**单向条纹**
        //   externalScale=100 ⇒ 跨十几~几十个瓦片（每像素随机）⇒ **细碎噪点**
        //   externalScale≈16  ⇒ 图标横跨约 2~6 个 16×16 瓦片 ⇒ 能分辨若干星点 + 星云（目标观感）
        // yaw/pitch 固定 0：只让 time 推动 v，星点在图标上慢慢游动，转视角不重排。
        boolean icon = group.startsWith("item_");
        RenderType chosen = icon ? cosmicIconRenderType() : cosmicRenderType();
        if (chosen == null) {
            return false;
        }
        float scale = icon ? ICON_EXTERNAL_SCALE : 1.0F;
        float yaw = icon ? 0.0F : (float) (mc.player.getYRot() * 2.0 * Math.PI / 360.0);
        float pitch = icon ? 0.0F : -((float) (mc.player.getXRot() * 2.0 * Math.PI / 360.0));
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255.0F)));
        float time = mc.level.getGameTime() % 2147483647L;
        // ⚠️⚠️ 顺序必须自己定死：26.1 的自定义几何按 RenderType 分桶、**桶间顺序由
        // RenderType 的 identity hash 决定（每次启动都可能不同）**，所以「星空层 vs 本体层
        // 谁先画」根本不可依赖 —— 这一路「一会只见星空、一会只见刀身」就是这么来回横跳的。
        // vanilla 所有提交都走 order(0)，这里走 order(1) ⇒ 本层永远排在本帧所有其它几何之后，
        // 配合「不写深度」+ 加法混合 = 1.20.1 里「TAIL 再叠一层」的等价语义（三层共存，稳定）。
        // ⚠️ 图标同样要 order(1)：26.1 的 GUI 物品渲染（OversizedItemRenderer）用的是**同一个**
        // SubmitNodeStorage + FeatureRenderDispatcher（先 submit 再 renderAllFeatures），
        // 留 order(0) 的话星空层和 item_* 平面同桶、谁先画还是 identity hash 决定 ⇒
        // 一半启动被图标盖住 = 「只能看到一点效果」。
        final RenderType renderType = chosen;
        // 前置的 submitCustomGeometry 必须用回调给的 pose（否则整层铺满屏，历史踩坑）
        collector.order(1).submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            // ⚠️ Face 的静态算子是全局可变状态：SlashEffectRenderer 等渲染路径会设置
            // alphaOverrideYZZ（按 Y/Z 空间改 alpha）/ uvOperator，残留时我们的 tessellation
            // 会继承它们 → 部分面 alpha=0 隐形 = 细长三角形空缺。1.20.1 原版
            // renderCosmicLegacy 里那套 setCol/setLightMap/reset* 反射调用就是干这个中和的。
            // 存档 → 中和 → tessellate → finally 还原（还原不是重置：外层可能还有包围圈）。
            var prevAlpha = Face.alphaOverride;
            var prevUv = Face.uvOperator;
            Face.alphaOverride = Face.alphaNoOverride;
            Face.uvOperator = Face.uvDefaultOperator;
            try {
                PoseStack local = new PoseStack();
                local.last().pose().set(pose.pose());
                local.last().normal().set(pose.normal());
                model.tessellateOnly(new CosmicParamsConsumer(consumer, time, yaw, pitch, scale, alpha),
                        local, MAX_LIGHT, 0, group);
            } finally {
                Face.alphaOverride = prevAlpha;
                Face.uvOperator = prevUv;
            }
        });
        return true;
    }

    private static void rebuild() {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(PIPELINE_ID)
                .withVertexShader(SHADER_ID)
                .withFragmentShader(SHADER_ID)
                // 原版 RenderStateShard：NO_CULL + LIGHTNING_TRANSPARENCY（f_110136_）。
                // 26.1 直系继任 = BlendFunction.LIGHTNING（srcColor/ONE 加法混合）：
                // 暗星云自乘后≈0.01-0.09 = 原版「低透明度紫」观感的真正来源；
                // 亮星星符号照常突出。alpha 通道在闪电混合下不参与（同原版）。
                .withCull(false)
                // ⚠️ ENTITY_SNIPPET 的顶点模式是 QUADS（vanilla 实体模型按四边形发射），
                // 而 SlashBlade 的 OBJ 模型 tessellation 每面发射 3 顶点（三角形流）——
                // 不改模式的话 collector 按 QUADS 开 buffer，三角形流被每 4 顶点强行
                // 重组 = 跨面碎三角（上游刀也碎、碎法随模型而异、侧立长三角、细长空缺；
                // 圆盘正常是因为它自己 begin(TRIANGLES)）。
                .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.ENTITY,
                        com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES)
                // ⚠️⚠️ 加法叠层必须「只写颜色、不写深度」（= 1.20.1 原版复合状态里的
                // LEQUAL_DEPTH_TEST + WriteMaskStateShard.COLOR_WRITE）。
                // 26.1 从 ENTITY_SNIPPET 继承来的 DepthStencilState.DEFAULT 是
                // (LESS_THAN_OR_EQUAL, writeDepth=**true**)；而 26.1 的批提交不保序
                // （同帧内各 RenderType 的绘制顺序由 map 决定）⇒ 星空层一旦排在本体层
                // 前面，加上 VIEW_OFFSET_Z_LAYERING 的微偏移，本体层的每个片段都会
                // 卡在 LEQUAL 上被丢弃 = **「只见星空、刀身模型消失」**（2026-11-03 实机）。
                // 不写深度后谁先谁后就都无所谓了。
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .withColorTargetState(new ColorTargetState(java.util.Optional.of(BlendFunction.LIGHTNING),
                        ColorTargetState.WRITE_COLOR));
        for (int i = 0; i < 10; i++) {
            builder.withShaderDefine("CUV" + i + "_U0", COSMIC_UVS[i * 4]);
            builder.withShaderDefine("CUV" + i + "_V0", COSMIC_UVS[i * 4 + 1]);
            builder.withShaderDefine("CUV" + i + "_U1", COSMIC_UVS[i * 4 + 2]);
            builder.withShaderDefine("CUV" + i + "_V1", COSMIC_UVS[i * 4 + 3]);
        }
        pipeline = builder.build();
        // 世界路径：星空层与本体贴图层是同一几何、同深度 ⇒ 视图空间 Z 偏移把它微微推开
        //（1.20.1 靠「TAIL 后绘制」稳赢，26.1 靠 order(1) 已能定序，这里保留一点余量）。
        RenderSetup worldSetup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", TextureAtlas.LOCATION_ITEMS)
                .useLightmap()
                .useOverlay()
                .setLayeringTransform(net.minecraft.client.renderer.rendertype.LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .createRenderSetup();
        cosmicRenderType = RenderType.create("shizuku_cosmic", worldSetup);
        // 图标路径：**不带 layering transform**（1.20.1 / 1.21.1 的 COSMIC_RENDER_TYPE 本来就没有
        // 这个 shard）。GUI 是正交投影，VIEW_OFFSET_Z_LAYERING 在正交下会被换算成完全不同的
        // Z 位移，容易让图标那层被 LEQUAL 挡掉 ⇒ 只剩边角一点星空（用户实测「差点意思」）。
        RenderSetup iconSetup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", TextureAtlas.LOCATION_ITEMS)
                .useLightmap()
                .useOverlay()
                .createRenderSetup();
        cosmicIconRenderType = RenderType.create("shizuku_cosmic_icon", iconSetup);
    }

    /**
     * 每帧读 10 个 sprite（map 查找）填 {@link #COSMIC_UVS}；uvs 与现有管线不一致
     * （资源重载重缝图集）时重建管线。返回 false = 精灵未缝入。
     */
    private static boolean collectSprites() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        TextureAtlas atlas;
        try {
            // ⚠️ getAtlasOrThrow 查 atlasById 映射，键 = 图集 json id（minecraft:items），
            // 不是贴图路径 textures/atlas/items.png（LOCATION_ITEMS）——传错直接 IllegalArgumentException。
            atlas = mc.getAtlasManager().getAtlasOrThrow(ITEMS_ATLAS_ID);
        } catch (Exception e) {
            LOGGER.error("Failed to look up items atlas by id {}", ITEMS_ATLAS_ID, e);
            return false;
        }
        for (int i = 0; i < 10; i++) {
            TextureAtlasSprite sprite = atlas.getSprite(shizuku.prefix("item/misc/cosmic_" + i));
            float u0 = sprite.getU0(), v0 = sprite.getV0(), u1 = sprite.getU1(), v1 = sprite.getV1();
            if (COSMIC_UVS[i * 4] != u0 || COSMIC_UVS[i * 4 + 1] != v0
                    || COSMIC_UVS[i * 4 + 2] != u1 || COSMIC_UVS[i * 4 + 3] != v1) {
                COSMIC_UVS[i * 4] = u0;
                COSMIC_UVS[i * 4 + 1] = v0;
                COSMIC_UVS[i * 4 + 2] = u1;
                COSMIC_UVS[i * 4 + 3] = v1;
                if (cosmicRenderType != null) {
                    rebuild();  // 图集重缝：精灵位置变了，重编管线里的 uv 常量
                }
            }
        }
        // 缺失检测：getSprite 回落是同一张缺失贴图（16×16 棋盘格，非退化矩形！），
        // ⇒ 10 个矩形全部相同 = 没缝进图集（判「退化」是错的，之前就因此漏判）
        for (int i = 1; i < 10; i++) {
            if (COSMIC_UVS[i * 4] != COSMIC_UVS[0] || COSMIC_UVS[i * 4 + 1] != COSMIC_UVS[1]
                    || COSMIC_UVS[i * 4 + 2] != COSMIC_UVS[2] || COSMIC_UVS[i * 4 + 3] != COSMIC_UVS[3]) {
                return true;  // 至少一个矩形不同 = 正常缝入
            }
        }
        return false;
    }

    /**
     * 参数通道 wrapper：把 cosmic 参数写进着色器不读的顶点属性
     * （UV0=(time,yaw)、Normal=(pitch,externalScale,0)、Color.a=opacity），其余属性原样转发。
     *
     * <p>1.20.1 是 per-draw uniform，26.1 的 RenderType 路径没有 uniform 钩子 ⇒ 走闲置属性槽。
     * opacity 是原版 {@code prepareCosmicRender(opacity)} 的等价物（刀身恒 1.0，
     * 斩击/刀光传 {@code baseAlpha}）。</p>
     */
    public static final class CosmicParamsConsumer implements VertexConsumer {

        private final VertexConsumer inner;
        private final float time;
        private final float yaw;
        private final float pitch;
        private final float externalScale;
        private final int alpha;

        public CosmicParamsConsumer(VertexConsumer inner, float time, float yaw, float pitch,
                                    float externalScale, int alpha) {
            this.inner = inner;
            this.time = time;
            this.yaw = yaw;
            this.pitch = pitch;
            this.externalScale = externalScale;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.inner.setColor(r, g, b, this.alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            this.inner.setColor((color & 0x00FFFFFF) | (this.alpha << 24));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            // 模型 UV 被着色器忽略（原版也不采样刀身贴图），此槽位改传 (time, yaw)
            this.inner.setUv(this.time, this.yaw);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.inner.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            // 模型法线被着色器忽略，此槽位改传 (pitch, externalScale, 0)
            this.inner.setNormal(this.pitch, this.externalScale, 0.0F);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            this.inner.setLineWidth(width);
            return this;
        }
    }
}
