package com.buwan.shizuku.client.shader;

import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
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

    public static final Identifier SHADER_ID = shizuku.prefix("core/cosmic");
    public static final Identifier PIPELINE_ID = shizuku.prefix("pipeline/cosmic");

    /** 与原版同序：[i*4]=U0, +1=V0, +2=U1, +3=V1（对应 fsh mat2(U0,V0,U1,V1) 的列主序）。 */
    public static final float[] COSMIC_UVS = new float[40];

    private static RenderPipeline pipeline;
    private static RenderType cosmicRenderType;
    private static boolean spritesWarned;

    private CosmicShaders() {
    }

    /** 宇宙星空 RenderType（Sampler0 = 方块图集；圆盘与刀身共用，同原版 COSMIC_RENDER_TYPE）。 */
    public static RenderType cosmicRenderType() {
        if (!collectSprites()) {
            if (!spritesWarned) {
                spritesWarned = true;
                LOGGER.warn("Cosmic sprites not stitched into block atlas (assets/shizuku/atlases/blocks.json?)");
            }
            return null;
        }
        if (cosmicRenderType == null) {
            rebuild();
        }
        return cosmicRenderType;
    }

    /** 兼容旧调用（刀身路径，当前休眠）：原版刀身也用同一个图集 RenderType，不采样本体贴图。 */
    public static RenderType renderTypeFor(Identifier texture) {
        return cosmicRenderType();
    }

    private static void rebuild() {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(PIPELINE_ID)
                .withVertexShader(SHADER_ID)
                .withFragmentShader(SHADER_ID)
                // 原版 RenderStateShard 是 NO_CULL
                .withCull(false);
        for (int i = 0; i < 10; i++) {
            builder.withShaderDefine("CUV" + i + "_U0", COSMIC_UVS[i * 4]);
            builder.withShaderDefine("CUV" + i + "_V0", COSMIC_UVS[i * 4 + 1]);
            builder.withShaderDefine("CUV" + i + "_U1", COSMIC_UVS[i * 4 + 2]);
            builder.withShaderDefine("CUV" + i + "_V1", COSMIC_UVS[i * 4 + 3]);
        }
        pipeline = builder.build();
        RenderSetup setup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                .useLightmap()
                .useOverlay()
                .createRenderSetup();
        cosmicRenderType = RenderType.create("shizuku_cosmic", setup);
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
            atlas = mc.getAtlasManager().getAtlasOrThrow(TextureAtlas.LOCATION_BLOCKS);
        } catch (Exception e) {
            return false;
        }
        boolean allSame = true;
        for (int i = 0; i < 10; i++) {
            TextureAtlasSprite sprite = atlas.getSprite(shizuku.prefix("item/misc/cosmic_" + i));
            float u0 = sprite.getU0(), v0 = sprite.getV0(), u1 = sprite.getU1(), v1 = sprite.getV1();
            if (u0 != u1 || v0 != v1) {
                allSame = false;
            }
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
        // 全部退化为同一点 = 没缝进去（getSprite 回落成同一张缺失贴图）
        return !allSame || COSMIC_UVS[0] != COSMIC_UVS[4];
    }
}
