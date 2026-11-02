package com.buwan.shizuku.client.shader;

import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.function.Function;

/**
 * 宇宙刀着色器（Fabric 版）。
 *
 * 与 NeoForge 版的差异：Fabric 没有 {@code RegisterShadersEvent}，
 * 这里用公开构造器 {@code ShaderInstance(ResourceProvider, id, format)} 懒创建，
 * 图集 sprite 也在首次使用时从 BLOCK_ATLAS 懒取。
 */
public final class CosmicShaders {

    /** 10 个星图 sprite 的 UV 矩阵（每个 mat2 两列：u0/v0, u1/v1） */
    public static final float[] COSMIC_UVS = new float[40];
    public static final TextureAtlasSprite[] COSMIC_SPRITES = new TextureAtlasSprite[10];
    public static ShaderInstance COSMIC_SHADER;
    public static AbstractUniform cosmicTime;
    public static AbstractUniform cosmicYaw;
    public static AbstractUniform cosmicPitch;
    public static AbstractUniform cosmicExternalScale;
    public static AbstractUniform cosmicOpacity;
    public static AbstractUniform cosmicUVs;
    public static boolean inventoryRender = false;
    public static final String COSMIC_BLADE_KEY = "item.shizuku.mosmicblade";

    private static boolean spritesLoaded = false;

    private static final RenderStateShard.EmptyTextureStateShard COSMIC_TEXTURE_ISOLATED =
            new RenderStateShard.EmptyTextureStateShard(() -> {
                RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
                RenderSystem.setShaderTexture(1, InventoryMenu.BLOCK_ATLAS);
            }, () -> {
            });

    public static final RenderType COSMIC_RENDER_TYPE = RenderType.create(
            "shizuku_cosmic",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.TRIANGLES,
            4096,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(CosmicShaders::ensureShader))
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTextureState(COSMIC_TEXTURE_ISOLATED)
                    .createCompositeState(false)
    );

    public static final RenderType COSMIC_RENDER_TYPE_NO_DEPTH = RenderType.create(
            "shizuku_cosmic_no_depth",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.TRIANGLES,
            4096,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(CosmicShaders::ensureShader))
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTextureState(COSMIC_TEXTURE_ISOLATED)
                    .createCompositeState(false)
    );

    private CosmicShaders() {
    }

    /** 懒创建着色器；失败返回 null（调用方据此跳过渲染）。 */
    public static ShaderInstance ensureShader() {
        if (COSMIC_SHADER == null) {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc == null || mc.getResourceManager() == null) {
                    return null;
                }
                COSMIC_SHADER = new ShaderInstance(mc.getResourceManager(),
                        shizuku.prefix("cosmic").toString(), DefaultVertexFormat.POSITION_TEX);
                cosmicTime = COSMIC_SHADER.safeGetUniform("time");
                cosmicYaw = COSMIC_SHADER.safeGetUniform("yaw");
                cosmicPitch = COSMIC_SHADER.safeGetUniform("pitch");
                cosmicExternalScale = COSMIC_SHADER.safeGetUniform("externalScale");
                cosmicOpacity = COSMIC_SHADER.safeGetUniform("opacity");
                cosmicUVs = COSMIC_SHADER.safeGetUniform("cosmicuvs");
            } catch (Exception e) {
                return null;
            }
        }
        return COSMIC_SHADER;
    }

    /** 首次使用时从方块图集取 cosmic_0..9 的 UV。 */
    private static void ensureSprites() {
        if (spritesLoaded) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        Function<Identifier, TextureAtlasSprite> atlas = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        for (int i = 0; i < COSMIC_SPRITES.length; i++) {
            TextureAtlasSprite sprite = atlas.apply(shizuku.prefix("item/misc/cosmic_" + i));
            COSMIC_SPRITES[i] = sprite;
            COSMIC_UVS[i * 4] = sprite.getU0();
            COSMIC_UVS[i * 4 + 1] = sprite.getV0();
            COSMIC_UVS[i * 4 + 2] = sprite.getU1();
            COSMIC_UVS[i * 4 + 3] = sprite.getV1();
        }
        spritesLoaded = true;
    }

    public static boolean prepareCosmicRender(float opacity) {
        return prepareCosmicRender(opacity, inventoryRender);
    }

    /**
     * @param fixedView true=GUI 固定视角（yaw/pitch 归零、scale 100）；false=跟随玩家视角（世界特效）
     */
    public static boolean prepareCosmicRender(float opacity, boolean fixedView) {
        if (ensureShader() == null) {
            return false;
        }
        ensureSprites();
        if (cosmicTime == null || cosmicYaw == null || cosmicPitch == null
                || cosmicExternalScale == null || cosmicOpacity == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return false;
        }
        float yaw;
        float pitch;
        float scale;
        if (fixedView) {
            scale = 100.0F;
            yaw = 0.0F;
            pitch = 0.0F;
        } else {
            scale = 1.0F;
            yaw = (float) (mc.player.getYRot() * 2.0 * Math.PI / 360.0);
            pitch = -((float) (mc.player.getXRot() * 2.0 * Math.PI / 360.0));
        }

        cosmicTime.set((float) (mc.level.getGameTime() % 2147483647L));
        cosmicYaw.set(yaw);
        cosmicPitch.set(pitch);
        cosmicExternalScale.set(scale);
        cosmicOpacity.set(opacity);
        if (cosmicUVs != null) {
            cosmicUVs.set(COSMIC_UVS);
        }
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
