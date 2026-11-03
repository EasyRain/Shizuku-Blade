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

    /** 图标版管线 id（与世界版共用源码，靠 COSMIC_ICON define 分叉，因此要独立 id）。 */
    public static final Identifier ICON_PIPELINE_ID = shizuku.prefix("pipeline/cosmic_icon");

    /** 满亮度（= 1.20.1 原版 renderCosmic 里写死的 15728880）。 */
    public static final int MAX_LIGHT = 15728880;

    /**
     * 物品图标专用的 externalScale（世界的 cosmic 参数是 1.0，1.20.1 的 inventoryRender 预设是 100）。
     * 图标射线已按分组 pose 后跨度归一（锥角恒定 ⇒ Δrawu ≈ 0.25），本值是纯粹的密度旋钮：
     * 图标横跨瓦片数 ≈ 0.25 × scale(3.25~10.75) × 本值 × 16 ⇒ **0.3 ≈ 平均 8×5 个瓦片/层**
     * （= 实测标定过的「拼块式星空：能分辨星点 + 星云」密度；嫌粗调大、嫌细调小，不再随路径漂移）。
     */
    public static final float ICON_EXTERNAL_SCALE = 0.3F;

    /**
     * 图标合成射线的**目标半径**：把每个分组 pose 变换后的 XY 跨度归一到 ~±1（锥角 ≈ ±45°，
     * Δrawu ≈ 0.25）。之前用固定 spread 直接乘模型坐标，锥角被驱动方的 pose 缩放绑架——
     * 快捷栏 PIP 与背包格 PIP 是两个不同视口缩放 ⇒ 一个星点碎片化、一个星星大到发白，
     * ES 怎么调都随路径漂移（这就是「调不出稳定好看星空」的结构性原因）。归一化后
     * 星点密度与驱动方/视口无关，{@link #ICON_EXTERNAL_SCALE} 才成为纯粹的密度旋钮。
     */
    public static final float ICON_RAY_TARGET = 1.0F;

    /** 拿不到分组包围盒时的兜底 spread（≈ 老的 0.012：item_* 平面 ±80 的标定值）。 */
    public static final float ICON_FALLBACK_SPREAD = 0.012F;

    /**
     * 图标瓦片取样内缩（0.6 = 只采样每张星点精灵的中心 60%）：星点美术画到了 16×16 画布
     * 边缘，图标接缝/瓦片边界处会把边缘星切成**半星**（世界路径星点太小看不出来；图标放大
     * 后就是「有些星点只显示半边」）。收进中心后边缘内容永不被采样，剩下的都是完整星点
     * （稍小、更接近 1.21.1 NeoForge 的大块星云观感）。
     */
    public static final float ICON_SPRITE_CENTER = 0.6F;

    public static final Identifier SHADER_ID = shizuku.prefix("core/cosmic");
    public static final Identifier PIPELINE_ID = shizuku.prefix("pipeline/cosmic");

    /** 与原版同序：[i*4]=U0, +1=V0, +2=U1, +3=V1（对应 fsh mat2(U0,V0,U1,V1) 的列主序）。 */
    public static final float[] COSMIC_UVS = new float[40];

    /** 图标版用的 uv：只取每张动画精灵的**第 0 帧**子矩形（见 collectSprites 的注释）。 */
    public static final float[] ICON_UVS = new float[40];

    private static RenderPipeline pipeline;
    /** 图标版管线（同 shader 源码 + COSMIC_ICON define）。 */
    private static RenderPipeline iconPipeline;
    private static RenderType cosmicRenderType;
    /** 物品图标专用（与 {@link #cosmicRenderType} 同管线，只是不带 layering transform）。 */
    private static RenderType cosmicIconRenderType;
    private static boolean spritesWarned;

    /** 一次性诊断：每个 (group, icon) 组合只打一条日志，确认各路径的分组来源（容器格/掉落物/…）。 */
    private static final java.util.Set<String> SUBMIT_DEBUG_LOGGED =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /**
     * 「正在 GuiRenderer.render 里」= GUI 上下文（26.1 的 inventoryRender 等价物，由
     * {@code GuiRendererCosmicMixin} 置位/复位）。期间发出的所有 cosmic 层都按图标
     * （固定视角）处理：快捷栏 PIP、背包/容器格子、JEI、GuiItemAtlas 烘焙全覆盖。
     */
    private static boolean inGuiRender;

    public static void beginGuiRender() {
        inGuiRender = true;
    }

    public static void endGuiRender() {
        inGuiRender = false;
    }

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
        // ⚠️ 图标判定改用「渲染阶段」而不是分组名（= 1.20.1/1.21.1 inventoryRender 的迁移）：
        // GuiRendererCosmicMixin 在 GuiRenderer.render 期间置位 —— 快捷栏（PIP 实时）、
        // 背包/容器格子、JEI、GuiItemAtlas 烘焙期间发出的一切 RenderOverrideEvent 全覆盖。
        // 旧写法 group.startsWith("item_") 的两个坑：容器格子走 3D 路径发 blade_luminous
        // ⇒ 漏判成世界层（白横条 + 随视角闪烁）；地面掉落物发 item_* ⇒ 误判成图标（该流动的
        // 世界星点被冻住）。按阶段判与原版语义一致（世界渲染在本帧更早处完成，标志必然 false）。
        boolean icon = inGuiRender;
        float spreadX = ICON_FALLBACK_SPREAD;
        float spreadY = ICON_FALLBACK_SPREAD;
        if (icon) {
            float[] spreads = iconSpreads(model, group, poseStack);
            if (spreads != null) {
                spreadX = spreads[0];
                spreadY = spreads[1];
                // 去重键带上（取整后的）pose 跨度：快捷栏 / 背包格 / JEI 的视口缩放不同会各记一条
                if (SUBMIT_DEBUG_LOGGED.add(group + "|icon|" + Math.round(spreads[2] * 1000.0F)
                        + "|" + Math.round(spreads[3] * 1000.0F))) {
                    LOGGER.info("[cosmic-submit] group={} icon=true ext=[{},{}] spread=[{},{}] caller={}",
                            group, spreads[2], spreads[3], spreadX, spreadY, callerChain());
                }
            } else if (SUBMIT_DEBUG_LOGGED.add(group + "|icon|nobounds")) {
                LOGGER.info("[cosmic-submit] group={} icon=true NO-BOUNDS(fallback) caller={}",
                        group, callerChain());
            }
        } else if (SUBMIT_DEBUG_LOGGED.add(group + "|world")) {
            LOGGER.info("[cosmic-submit] group={} icon=false caller={}", group, callerChain());
        }
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
        // lambda 捕获要求 effectively final：归一化 spread 在上面条件赋值，这里定格一份
        final float spreadXf = spreadX;
        final float spreadYf = spreadY;
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
                model.tessellateOnly(new CosmicParamsConsumer(consumer, time, yaw, pitch, scale, alpha, spreadXf, spreadYf),
                        local, MAX_LIGHT, 0, group);
            } finally {
                Face.alphaOverride = prevAlpha;
                Face.uvOperator = prevUv;
            }
        });
        return true;
    }

    /** (model, group) → 原始顶点 XY 包围盒（minX,minY,maxX,maxY）缓存；模型来自 BladeModelManager 的 LoadingCache，身份稳定。 */
    private static final java.util.Map<WavefrontObject, java.util.Map<String, float[]>> RAW_GROUP_BOUNDS =
            new java.util.WeakHashMap<>();

    /**
     * 图标合成射线的归一化 spread：取分组原始 XY 包围盒的 4 角，经当前 pose 变换后的跨度
     * （两轴各自的最大绝对值），除 {@link #ICON_RAY_TARGET} ⇒ 无论驱动方把模型缩放到多小
     * （快捷栏 PIP / 背包格 PIP / 图集烘焙各不相同），射线锥角恒定、星点密度恒定。
     * 返回 {@code [spreadX, spreadY, extX, extY]}；找不到分组返回 null。
     */
    private static float[] iconSpreads(WavefrontObject model, String group, PoseStack poseStack) {
        java.util.Map<String, float[]> byGroup = RAW_GROUP_BOUNDS.computeIfAbsent(model, m -> new java.util.HashMap<>());
        float[] raw = byGroup.get(group);
        if (raw == null) {
            raw = rawGroupBounds(model, group);
            byGroup.put(group, raw);
        }
        if (raw == null) {
            return null;
        }
        org.joml.Matrix4f mat = poseStack.last().pose();
        org.joml.Vector3f v = new org.joml.Vector3f();
        float extX = 1.0E-4F;
        float extY = 1.0E-4F;
        // raw = {minX, minY, maxX, maxY}，四个角 = 两个 min/max 的组合（不是 8 个连续坐标——
        // 19:35 崩溃就是把它当 8 元素数组读导致的越界）
        for (int i = 0; i < 4; i++) {
            float cx = (i & 1) == 0 ? raw[0] : raw[2];
            float cy = (i & 2) == 0 ? raw[1] : raw[3];
            mat.transformPosition(cx, cy, 0.0F, v);
            extX = Math.max(extX, Math.abs(v.x));
            extY = Math.max(extY, Math.abs(v.y));
        }
        return new float[]{ICON_RAY_TARGET / extX, ICON_RAY_TARGET / extY, extX, extY};
    }

    /** 分组原始顶点的 XY 包围盒（图标平面是 2D，z 不参与射线）；找不到分组返回 null。 */
    private static float[] rawGroupBounds(WavefrontObject model, String group) {
        if (model.groupObjects == null) {
            return null;
        }
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        boolean any = false;
        for (var go : model.groupObjects) {
            if (!group.equals(go.name) || go.faces == null) {
                continue;
            }
            for (var face : go.faces) {
                if (face == null || face.vertices == null) {
                    continue;
                }
                for (var vtx : face.vertices) {
                    if (vtx == null) {
                        continue;
                    }
                    any = true;
                    minX = Math.min(minX, vtx.x);
                    maxX = Math.max(maxX, vtx.x);
                    minY = Math.min(minY, vtx.y);
                    maxY = Math.max(maxY, vtx.y);
                }
            }
        }
        return any ? new float[]{minX, minY, maxX, maxY} : null;
    }

    /** 诊断用：调用链头几个非本包帧（一眼看出这次提交来自 TEISR/GroundRenderer/LayerMainBlade 哪条路）。 */
    private static String callerChain() {
        StackTraceElement[] st = new Throwable().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int printed = 0;
        for (int i = 1; i < st.length && printed < 6; i++) {
            String cls = st[i].getClassName();
            if (cls.startsWith("com.buwan.shizuku.")) {
                continue;
            }
            int dot = cls.lastIndexOf('.');
            if (printed > 0) {
                sb.append(" <- ");
            }
            sb.append(dot >= 0 ? cls.substring(dot + 1) : cls).append(':').append(st[i].getMethodName());
            printed++;
        }
        return sb.toString();
    }

    private static void rebuild() {
        pipeline = buildPipeline(false);
        iconPipeline = buildPipeline(true);
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
        RenderSetup iconSetup = RenderSetup.builder(iconPipeline)
                .withTexture("Sampler0", TextureAtlas.LOCATION_ITEMS)
                .useLightmap()
                .useOverlay()
                .createRenderSetup();
        cosmicIconRenderType = RenderType.create("shizuku_cosmic_icon", iconSetup);
    }

    /**
     * 同一个着色器编出两份管线：世界版 + 图标版（图标版多两个 define，见 cosmic.vsh 的
     * {@code #ifdef COSMIC_ICON}：把射线来源换成模型空间坐标，避免 GUI 正交下只放大一小块天球）。
     */
    private static RenderPipeline buildPipeline(boolean icon) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(icon ? ICON_PIPELINE_ID : PIPELINE_ID)
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
        if (icon) {
            builder.withShaderDefine("COSMIC_ICON", 1.0F);
            builder.withShaderDefine("COSMIC_ICON_INSET", ICON_SPRITE_CENTER);
            // 图标用「第 0 帧」子矩形（多帧动画精灵整块采样会被压成白横条）
            for (int i = 0; i < 10; i++) {
                builder.withShaderDefine("ICUV" + i + "_U0", ICON_UVS[i * 4]);
                builder.withShaderDefine("ICUV" + i + "_V0", ICON_UVS[i * 4 + 1]);
                builder.withShaderDefine("ICUV" + i + "_U1", ICON_UVS[i * 4 + 2]);
                builder.withShaderDefine("ICUV" + i + "_V1", ICON_UVS[i * 4 + 3]);
            }
        }
        return builder.build();
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
            // ⚠️ 这 10 张星点精灵**全是动画贴图**（16 宽 × N×16 高，帧竖着堆在精灵区域内，
            // cosmic_8 有 7 帧）。整块精灵矩形 = 把 N 帧挤进一个瓦片 ⇒ 在世界几何上只是「小星点」，
            // 但在被放大几十倍的物品图标上就是**一条白色横条**（这也解释了「前十几秒好看、
            // 之后变横条碎块」：time 推动 v 漂到多帧精灵所在的瓦片就变难看）。
            // 图标版只取第 0 帧的子矩形：帧数 = v 跨度 / u 跨度（帧是正方形，无 padding）。
            float frames = Math.max(1.0F, Math.round((v1 - v0) / Math.max(1.0E-6F, u1 - u0)));
            float frameV1 = v0 + (v1 - v0) / frames;
            if (ICON_UVS[i * 4] != u0 || ICON_UVS[i * 4 + 1] != v0
                    || ICON_UVS[i * 4 + 2] != u1 || ICON_UVS[i * 4 + 3] != frameV1) {
                ICON_UVS[i * 4] = u0;
                ICON_UVS[i * 4 + 1] = v0;
                ICON_UVS[i * 4 + 2] = u1;
                ICON_UVS[i * 4 + 3] = frameV1;
                if (cosmicIconRenderType != null) {
                    rebuild();
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
        /** 图标分支的归一化射线张角（按位打包进 UV1；世界分支的 vsh 不读 UV1）。 */
        private final float spreadX;
        private final float spreadY;

        public CosmicParamsConsumer(VertexConsumer inner, float time, float yaw, float pitch,
                                    float externalScale, int alpha, float spreadX, float spreadY) {
            this.inner = inner;
            this.time = time;
            this.yaw = yaw;
            this.pitch = pitch;
            this.externalScale = externalScale;
            this.alpha = alpha;
            this.spreadX = spreadX;
            this.spreadY = spreadY;
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
            // 图标分支：把归一化 spread 按位打包进着色器本来不读的 UV1（ivec2），vsh 用
            // intBitsToFloat 解包；世界分支的 vsh 同样不读 UV1（overlay 本来就被忽略），
            // 原值不必保留。
            this.inner.setUv1(Float.floatToRawIntBits(this.spreadX), Float.floatToRawIntBits(this.spreadY));
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
