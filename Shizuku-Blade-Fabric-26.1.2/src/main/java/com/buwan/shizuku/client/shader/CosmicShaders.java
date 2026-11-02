package com.buwan.shizuku.client.shader;

import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.TextureAtlas;

import java.util.function.Function;

/**
 * 宇宙刀着色器（26.1 降级实现）。
 *
 * <p>1.21.1 用 {@code ShaderInstance} + {@code RenderStateShard} 做自定义星空纹理动画；
 * 26.1 移除了这套 API（改由 {@code RenderPipeline} + 资源文件定义），
 * 这里改用现成的 {@link RenderTypes}（图集半透明），宇宙刀仍可正常渲染，
 * 仅失去星空流动特效。</p>
 */
public final class CosmicShaders {

    /** 10 个星图 sprite 的 UV 矩阵（保留字段以兼容调用方）。 */
    public static final float[] COSMIC_UVS = new float[40];
    public static final TextureAtlasSprite[] COSMIC_SPRITES = new TextureAtlasSprite[10];
    /** 26.1 无 ShaderInstance，保留非空占位让调用方的 null 检查通过。 */
    public static final Object COSMIC_SHADER = new Object();
    public static boolean inventoryRender = false;
    public static final String COSMIC_BLADE_KEY = "item.shizuku.mosmicblade";

    /** 宇宙刀使用图集半透明类型（原自定义着色器管线已不可用）。 */
    public static final RenderType COSMIC_RENDER_TYPE =
            RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);

    /** 星河领域圆盘使用无贴图彩色四边形（原 no-depth 自定义类型已不可用）。 */
    public static final RenderType COSMIC_RENDER_TYPE_NO_DEPTH =
            RenderTypes.debugQuads();

    private static boolean spritesLoaded = false;

    private CosmicShaders() {
    }

    /** 26.1 无需在代码里创建 ShaderInstance，保留方法供调用方兼容。 */
    public static Object ensureShader() {
        return COSMIC_SHADER;
    }

    /** 26.1 移除了 Minecraft#getTextureAtlas，星空 UV 不再参与渲染，这里仅置标记。 */
    private static void ensureSprites() {
        spritesLoaded = true;
    }

    public static boolean prepareCosmicRender(float opacity) {
        return prepareCosmicRender(opacity, inventoryRender);
    }

    /**
     * @param fixedView true=GUI 固定视角；false=跟随玩家视角（世界特效）
     */
    public static boolean prepareCosmicRender(float opacity, boolean fixedView) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return false;
        }
        ensureSprites();
        return true;
    }

    public static void renderCosmic(WavefrontObject model, String target, PoseStack poseStack,
                                    MultiBufferSource buffer, float opacity) {
        if (model != null && target != null) {
            if (prepareCosmicRender(opacity)) {
                VertexConsumer vb = buffer.getBuffer(COSMIC_RENDER_TYPE);
                model.tessellateOnly(vb, poseStack, 15728880, 0, target);
            }
        }
    }
}
