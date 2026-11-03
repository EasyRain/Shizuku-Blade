package com.buwan.shizuku.client.shader;

import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 宇宙刀/星河领域的自定义着色器（26.1）。
 *
 * <p>26.1 移除了 {@code ShaderInstance} 与 shader JSON，改为：</p>
 * <ol>
 *   <li>着色器源码仍放 {@code assets/<ns>/shaders/core/*.vsh|.fsh}（支持 {@code #moj_import}）；</li>
 *   <li>用 {@code RenderPipeline.builder} 在代码里声明管线（顶点/片元着色器、顶点格式、混合等）；</li>
 *   <li>用 {@code RenderSetup.builder} 绑定贴图，再交给 {@code RenderType.create} 生成 RenderType。</li>
 * </ol>
 *
 * <p>写法参考 26.1.2 Fabric 生态里实际可用的实现（physics-mod）：沿用
 * {@code RenderPipelines.ENTITY_SNIPPET} 提供的 uniform/采样器声明集合，只替换顶点与片元着色器，
 * 这样 {@code Sampler0}/{@code ModelViewMat} 等都由原版负责绑定。</p>
 *
 * <p>时间不进 uniform（26.1 要自建 RenderPass 才能绑 uniform），而是通过顶点属性 {@code UV2.x}
 * 传入，由 {@link com.buwan.shizuku.client.CosmicBladeRenderer} 写入。</p>
 */
public final class CosmicShaders {

    /** 「宇宙刀」对应的刀翻译键（判定哪些刀需要星光效果）。 */
    public static final String COSMIC_BLADE_KEY = "item.shizuku.mosmicblade";

    public static final Identifier SHADER_ID = shizuku.prefix("core/cosmic");
    public static final Identifier PIPELINE_ID = shizuku.prefix("pipeline/cosmic");

    /** 每张贴图对应一个 RenderType（管线共用）。 */
    private static final Map<Identifier, RenderType> RENDER_TYPES = new HashMap<>();

    private static RenderPipeline pipeline;

    private CosmicShaders() {
    }

    private static RenderPipeline pipeline() {
        if (pipeline == null) {
            pipeline = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(SHADER_ID)
                    .withFragmentShader(SHADER_ID)
                    .build();
        }
        return pipeline;
    }

    /** 取（或创建）指定贴图的宇宙刀 RenderType。 */
    public static RenderType renderTypeFor(Identifier texture) {
        return RENDER_TYPES.computeIfAbsent(texture, tex -> {
            RenderSetup setup = RenderSetup.builder(pipeline())
                    .withTexture("Sampler0", tex)
                    .useLightmap()
                    .useOverlay()
                    .createRenderSetup();
            return RenderType.create("shizuku_cosmic_" + tex.getPath().replace('/', '_'), setup);
        });
    }
}
