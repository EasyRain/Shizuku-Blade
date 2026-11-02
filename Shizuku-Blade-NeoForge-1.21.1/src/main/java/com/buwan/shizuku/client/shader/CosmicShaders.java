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
import net.minecraft.client.renderer.RenderStateShard.ShaderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;

@OnlyIn(Dist.CLIENT)
public final class CosmicShaders {

    /** 10 个星图 sprite 的 UV 矩阵（每个 mat2 两列：u0/v0, u1/v1） */
    public static final float[] COSMIC_UVS = new float[40];
    public static TextureAtlasSprite[] COSMIC_SPRITES = new TextureAtlasSprite[10];
    public static ShaderInstance COSMIC_SHADER;
    public static AbstractUniform cosmicTime;
    public static AbstractUniform cosmicYaw;
    public static AbstractUniform cosmicPitch;
    public static AbstractUniform cosmicExternalScale;
    public static AbstractUniform cosmicOpacity;
    public static AbstractUniform cosmicUVs;
    public static boolean inventoryRender = false;
    public static final String COSMIC_BLADE_KEY = "item.shizuku.mosmicblade";

    /**
     * 1.20.1 fix2 额外在 GL_TEXTURE1 绑定物品图集；
     * 1.21.1 的 TextureStateShard 无法覆写 setupState()，
     * 改为自定义 EmptyTextureStateShard：单元 0 + 单元 1 都绑定物品图集（保真行为）。
     */
    public static final RenderStateShard.EmptyTextureStateShard COSMIC_TEXTURE_ISOLATED =
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
                    .setShaderState(new ShaderStateShard(() -> COSMIC_SHADER))
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTextureState(COSMIC_TEXTURE_ISOLATED)
                    .createCompositeState(false)
    );

    /**
     * 光影（Iris/Oculus）下在 GUI 阶段重绘世界特效用的变体：
     * 此时屏幕帧缓冲没有可用深度，LEQUAL 深度测试会让几何随机被剔除，改用无深度测试。
     */
    public static final RenderType COSMIC_RENDER_TYPE_NO_DEPTH = RenderType.create(
            "shizuku_cosmic_no_depth",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.TRIANGLES,
            4096,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> COSMIC_SHADER))
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTextureState(COSMIC_TEXTURE_ISOLATED)
                    .createCompositeState(false)
    );

    private CosmicShaders() {
    }

    public static boolean prepareCosmicRender(float opacity) {
        return prepareCosmicRender(opacity, inventoryRender);
    }

    /**
     * @param fixedView true=GUI 固定视角（yaw/pitch 归零、scale 100）；false=跟随玩家视角（世界特效）
     */
    public static boolean prepareCosmicRender(float opacity, boolean fixedView) {
        if (COSMIC_SHADER == null) {
            return false;
        } else if (cosmicTime != null && cosmicYaw != null && cosmicPitch != null
                && cosmicExternalScale != null && cosmicOpacity != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
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
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * 1.21.1 Resharped 的 tessellateOnly 已带 (VertexConsumer, PoseStack, lightMap, color, targets…)，
     * 直接调用即可；不再需要 1.20.1 fix2 的反射兼容分支。
     */
    public static void renderCosmic(WavefrontObject model, String target, PoseStack poseStack, MultiBufferSource buffer, float opacity) {
        if (model != null && target != null) {
            if (prepareCosmicRender(opacity)) {
                VertexConsumer vb = buffer.getBuffer(COSMIC_RENDER_TYPE);
                model.tessellateOnly(vb, poseStack, 15728880, 0, target);
            }
        }
    }

    public static final class ForgeEvents {
        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onScreenPre(ScreenEvent.Render.Pre event) {
            CosmicShaders.inventoryRender = true;
        }

        @SubscribeEvent
        public static void onScreenPost(ScreenEvent.Render.Post event) {
            CosmicShaders.inventoryRender = false;
        }

        // 3.6.0 修复：快捷栏/HUD 内物品图标渲染时不经过 Screen 事件，
        // 原版在无屏幕时会用玩家实时 yaw/pitch → 转动视角导致图标闪烁。
        // 这里把 HUD 渲染期间也视为 GUI 上下文，屏蔽视角转动的影响。
        @SubscribeEvent
        public static void onRenderGuiPre(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
            CosmicShaders.inventoryRender = true;
        }

        @SubscribeEvent
        public static void onRenderGuiPost(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
            CosmicShaders.inventoryRender = false;
        }
    }

    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onRegisterShaders(RegisterShadersEvent event) {
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), shizuku.prefix("cosmic"), DefaultVertexFormat.POSITION_TEX), shader -> {
                    CosmicShaders.COSMIC_SHADER = shader;
                    CosmicShaders.cosmicTime = CosmicShaders.COSMIC_SHADER.safeGetUniform("time");
                    CosmicShaders.cosmicYaw = CosmicShaders.COSMIC_SHADER.safeGetUniform("yaw");
                    CosmicShaders.cosmicPitch = CosmicShaders.COSMIC_SHADER.safeGetUniform("pitch");
                    CosmicShaders.cosmicExternalScale = CosmicShaders.COSMIC_SHADER.safeGetUniform("externalScale");
                    CosmicShaders.cosmicOpacity = CosmicShaders.COSMIC_SHADER.safeGetUniform("opacity");
                    CosmicShaders.cosmicUVs = CosmicShaders.COSMIC_SHADER.safeGetUniform("cosmicuvs");
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @SubscribeEvent
        public static void onTextureStitch(TextureAtlasStitchedEvent event) {
            if (event.getAtlas().location().equals(InventoryMenu.BLOCK_ATLAS)) {
                for (int i = 0; i < CosmicShaders.COSMIC_SPRITES.length; i++) {
                    CosmicShaders.COSMIC_SPRITES[i] = event.getAtlas().getSprite(shizuku.prefix("item/misc/cosmic_" + i));
                    CosmicShaders.COSMIC_UVS[i * 4] = CosmicShaders.COSMIC_SPRITES[i].getU0();
                    CosmicShaders.COSMIC_UVS[i * 4 + 1] = CosmicShaders.COSMIC_SPRITES[i].getV0();
                    CosmicShaders.COSMIC_UVS[i * 4 + 2] = CosmicShaders.COSMIC_SPRITES[i].getU1();
                    CosmicShaders.COSMIC_UVS[i * 4 + 3] = CosmicShaders.COSMIC_SPRITES[i].getV1();
                }
            }
        }
    }
}
