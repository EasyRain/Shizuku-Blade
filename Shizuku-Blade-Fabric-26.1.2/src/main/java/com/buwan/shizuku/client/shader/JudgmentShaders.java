package com.buwan.shizuku.client.shader;

import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

/**
 * 审判的两个程序化着色器（26.1，1.20.1 原版忠实移植）。
 *
 * <p>1.20.1 的 {@code judgment_domain}（墨焰球壳）与 {@code judgment_rift}（玻璃裂缝）是
 * 两个 {@code ShaderInstance} + 自定义 {@code RenderType}。26.1 移除了 {@code ShaderInstance}，
 * 但保留了「core shader json + RenderPipeline + RenderType」这套（{@link CosmicShaders} 已实机验证
 * 可以出画面），所以这里照同一条路把它们搬过来。</p>
 *
 * <p>原版那两个着色器各有一个逐帧 uniform（{@code Atmosphere} / {@code Phase}），而 26.1 的
 * RenderType 只能给常量 —— 于是改由顶点属性 {@code Normal} 携带（.vsh 里读出来还原成同名变量），
 * 片元算法一行未改。</p>
 */
public final class JudgmentShaders {
    private JudgmentShaders() {}

    private static final Identifier DOMAIN_SHADER = shizuku.prefix("core/judgment_domain");
    private static final Identifier RIFT_SHADER = shizuku.prefix("core/judgment_rift");

    private static RenderType domainType;
    private static RenderType riftType;
    private static RenderType riftOffscreenType;

    /** 墨焰球壳（域球）。 */
    public static RenderType domain() {
        if (domainType == null) {
            domainType = build(DOMAIN_SHADER, "judgment_domain", true);
        }
        return domainType;
    }

    /** 玻璃裂缝（空间裂缝）——世界版：srcalpha 混合叠进场景。 */
    public static RenderType rift() {
        if (riftType == null) {
            riftType = build(RIFT_SHADER, "judgment_rift", true);
        }
        return riftType;
    }

    /**
     * 玻璃裂缝——离屏预览版：同一份 GLSL，但<b>无混合</b>直写。
     *
     * <p>rift fsh 输出直排 alpha；离屏目标透明清零后用无混合直写 = 存储原始 (rgb, a)，
     * GUI blit（{@code GUI_TEXTURED} 的 TRANSLUCENT）做<b>唯一一次</b>混合
     * {@code rgb*a + bg*(1-a)} —— 与世界版在场景上的混合链路完全一致。
     * 若沿用世界版的混合管线画进透明底，会存成预乘色+a²，GUI 再混合一次变成
     * {@code rgb*a³}，整个预览发淡（2026-11-24 judgworld4 部署前修正）。</p>
     */
    public static RenderType riftOffscreen() {
        if (riftOffscreenType == null) {
            riftOffscreenType = build(RIFT_SHADER, "judgment_rift_offscreen", false);
        }
        return riftOffscreenType;
    }

    /**
     * 与 {@link CosmicShaders} 完全同构的管线构建（ENTITY 顶点格式 / TRIANGLES / 无剔除 /
     * 不写深度 / 可关混合）。原版这两个效果都是半透明叠加，世界版用
     * {@link BlendFunction#TRANSLUCENT}；离屏版关混合（见 {@link #riftOffscreen()}）。
     */
    private static RenderType build(Identifier shader, String name, boolean blend) {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(shizuku.prefix("pipeline/" + name))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .withColorTargetState(blend
                        ? new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), ColorTargetState.WRITE_COLOR)
                        // 无混合直写必须 WRITE_ALL：alpha 只能靠片元写入，WRITE_COLOR(=7)
                        // 不写 alpha ⇒ 目标 alpha 恒为清屏值 0 ⇒ GUI blit 混 rgb*0 = 隐形
                        //（judgworld5「斩痕直接看不见」的根因）。世界版无所谓：main 的 alpha 不上屏。
                        : new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL))
                .build();
        RenderSetup setup = RenderSetup.builder(pipeline)
                // 着色器并不采样，但 RenderSetup 要求 Sampler0 有绑定（与 cosmic 一致用物品图集）
                .withTexture("Sampler0", TextureAtlas.LOCATION_ITEMS)
                .useLightmap()
                .useOverlay()
                .createRenderSetup();
        return RenderType.create("shizuku_" + name, setup);
    }
}
