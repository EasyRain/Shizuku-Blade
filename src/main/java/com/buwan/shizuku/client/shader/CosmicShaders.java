package com.buwan.shizuku.client.shader;

import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Color;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.RenderStateShard.ShaderStateShard;
import net.minecraft.client.renderer.RenderStateShard.TextureStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.ScreenEvent.Render.Post;
import net.minecraftforge.client.event.ScreenEvent.Render.Pre;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 星空（cosmic）着色器的总控。
 *
 * - COSMIC_RENDER_TYPE：为本 mod 单独建的 RenderType（带专用 TextureStateShard，
 *   绕过前置共享 RenderType 的 UV 缓存问题）；
 * - prepareCosmicRender / renderCosmic：上传 uniform（时间、yaw/pitch、外部缩放、透明度）
 *   并按 OBJ 的分组绘制刀身模型；
 * - ForgeEvents / ModEvents：订阅屏幕渲染前后与 shader 注册；
 * - inventoryRender：true 表示当前在画 GUI 里的物品图标，此时相机用固定视角（原版行为）。
 *
 * 注意：GUI 图标与世界中手持走的是同一套渲染调用，靠 inventoryRender 区分。
 */
@OnlyIn(Dist.CLIENT)
public final class CosmicShaders {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("shizuku/cosmicshaders");
    public static final float[] COSMIC_UVS = new float[40];
    public static TextureAtlasSprite[] COSMIC_SPRITES = new TextureAtlasSprite[10];
    public static ShaderInstance COSMIC_SHADER;
    public static Uniform cosmicTime;
    public static Uniform cosmicYaw;
    public static Uniform cosmicPitch;
    public static Uniform cosmicExternalScale;
    public static Uniform cosmicOpacity;
    public static Uniform cosmicUVs;
    public static boolean inventoryRender = false;
    public static final String COSMIC_BLADE_KEY = "item.shizuku.mosmicblade";
    public static final TextureStateShard COSMIC_TEXTURE_ISOLATED = new TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false) {
        @Override
        public void setupRenderState() {
            super.setupRenderState();
            RenderSystem.activeTexture(33984);
            Minecraft mc = Minecraft.getInstance();
            TextureAtlas textureAtlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
            RenderSystem.bindTexture(textureAtlas.getId());
        }
    };
    public static final RenderType COSMIC_RENDER_TYPE = RenderType.create(
        "shizuku_cosmic",
        DefaultVertexFormat.NEW_ENTITY,
        Mode.TRIANGLES,
        4096,
        false,
        false,
        CompositeState.builder()
            .setShaderState(new ShaderStateShard(() -> COSMIC_SHADER))
            .setDepthTestState(RenderShardAccess.LEQUAL_DEPTH_TEST)
            .setTransparencyState(RenderShardAccess.LIGHTNING_TRANSPARENCY)
            .setCullState(RenderShardAccess.NO_CULL)
            .setWriteMaskState(RenderShardAccess.COLOR_WRITE)
            .setTextureState(COSMIC_TEXTURE_ISOLATED)
            .createCompositeState(false)
    );
    private static final boolean OLD_FACE_API = hasOldFaceApi();

    private CosmicShaders() {
    }

    public static boolean prepareCosmicRender(float opacity) {
        if (COSMIC_SHADER == null) {
            return false;
        } else if (cosmicTime != null && cosmicYaw != null && cosmicPitch != null && cosmicExternalScale != null && cosmicOpacity != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                float yaw;
                float pitch;
                float scale;
                if (inventoryRender) {
                    scale = 100.0F;
                    yaw = 0.0F;
                    pitch = 0.0F;
                } else {
                    scale = 1.0F;
                    yaw = (float)(mc.player.getYRot() * 2.0 * Math.PI / 360.0);
                    pitch = -((float)(mc.player.getXRot() * 2.0 * Math.PI / 360.0));
                }

                cosmicTime.set((float)(mc.level.getGameTime() % 2147483647L));
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

    private static boolean hasOldFaceApi() {
        try {
            Face.class.getMethod("setCol", Color.class);
            return true;
        } catch (NoSuchMethodException var1) {
            return false;
        }
    }

    public static void renderCosmic(WavefrontObject model, String target, PoseStack poseStack, MultiBufferSource buffer, float opacity) {
        if (model != null && target != null) {
            if (prepareCosmicRender(opacity)) {
                VertexConsumer vb = buffer.getBuffer(COSMIC_RENDER_TYPE);
                if (OLD_FACE_API) {
                    renderCosmicLegacy(model, target, poseStack, vb);
                } else {
                    tessellateCosmicNewApi(model, target, poseStack, vb);
                }
            }
        }
    }

    private static void renderCosmicLegacy(WavefrontObject model, String target, PoseStack poseStack, VertexConsumer vb) {
        try {
            Class<?> face = Face.class;
            face.getMethod("setCol", Color.class).invoke(null, Color.WHITE);
            face.getMethod("setLightMap", int.class).invoke(null, 15728880);
            face.getMethod("setMatrix", PoseStack.class).invoke(null, poseStack);
            WavefrontObject.class.getMethod("tessellateOnly", VertexConsumer.class, String[].class).invoke(model, vb, new String[]{target});
            face.getMethod("resetMatrix").invoke(null);
            face.getMethod("resetLightMap").invoke(null);
            face.getMethod("resetCol").invoke(null);
        } catch (ReflectiveOperationException var5) {
        }
    }

    /**
     * 新版本 SlashBlade 把 PoseStack / lightmap 改成 tessellateOnly 的入参，
     * 而 1.4.x 只有 tessellateOnly(VertexConsumer, String...)。
     * 这里用反射调用 5 参重载，找不到时退回 2 参版本，两个版本都能编译且行为合理。
     * 注意：1.4.x 上 OLD_FACE_API 为 true，压根不会进这个分支。
     */
    private static void tessellateCosmicNewApi(WavefrontObject model, String target, PoseStack poseStack, VertexConsumer vb) {
        try {
            WavefrontObject.class
                .getMethod("tessellateOnly", VertexConsumer.class, PoseStack.class, int.class, int.class, String[].class)
                .invoke(model, vb, poseStack, 15728880, 0, new String[]{target});
        } catch (ReflectiveOperationException var5) {
            model.tessellateOnly(vb, target);
        }
    }

    public static final class ForgeEvents {
        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onScreenPre(Pre event) {
            CosmicShaders.inventoryRender = true;
        }

        @SubscribeEvent
        public static void onScreenPost(Post event) {
            CosmicShaders.inventoryRender = false;
        }
    }

    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onRegisterShaders(RegisterShadersEvent event) {
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), shizuku.prefix("cosmic"), DefaultVertexFormat.NEW_ENTITY), shader -> {
                    CosmicShaders.COSMIC_SHADER = shader;
                    CosmicShaders.cosmicTime = CosmicShaders.COSMIC_SHADER.getUniform("time");
                    CosmicShaders.cosmicYaw = CosmicShaders.COSMIC_SHADER.getUniform("yaw");
                    CosmicShaders.cosmicPitch = CosmicShaders.COSMIC_SHADER.getUniform("pitch");
                    CosmicShaders.cosmicExternalScale = CosmicShaders.COSMIC_SHADER.getUniform("externalScale");
                    CosmicShaders.cosmicOpacity = CosmicShaders.COSMIC_SHADER.getUniform("opacity");
                    CosmicShaders.cosmicUVs = CosmicShaders.COSMIC_SHADER.getUniform("cosmicuvs");
                });
            } catch (Exception var2) {
                LOGGER.debug("unexpected error", var2);
            }
        }

        @SubscribeEvent
        public static void onTextureStitch(net.minecraftforge.client.event.TextureStitchEvent.Post event) {
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
