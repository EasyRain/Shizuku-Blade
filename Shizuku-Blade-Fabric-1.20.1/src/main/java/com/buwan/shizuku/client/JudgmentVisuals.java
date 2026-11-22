package com.buwan.shizuku.client;

import com.buwan.shizuku.client.shader.RenderShardAccess;
import com.buwan.shizuku.network.JudgmentImpactPacket;
import com.buwan.shizuku.sa.Ymsa;
import com.buwan.shizuku.shizuku;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard.ShaderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = "shizuku", value = Dist.CLIENT)
public final class JudgmentVisuals {
    public static final ForgeConfigSpec.ConfigValue<Boolean> SCREEN_EFFECT;
    public static final ForgeConfigSpec.BooleanValue UNLEASHED;
    public static final ForgeConfigSpec.BooleanValue DAMAGE_PLAYERS;
    public static final ForgeConfigSpec CONFIG;
    private static final ItemStack DIAMOND_SWORD = new ItemStack(Items.DIAMOND_SWORD);
    private static final float SWORD_SCALE = 20;
    private static final float DROP_HEIGHT = 25;
    private record Cast(JudgmentImpactPacket packet, Vec3 center, long localStart, Vec3 landing) {}
    private static final Map<UUID, Cast> casts = new HashMap<>();
    private static ClientLevel currentLevel;
    private static ShaderInstance shader;
    private static ShaderInstance skyShader;
    private static VertexBuffer domainMesh;
    private static final RenderType RIFT = material("judgment_rift", new ShaderStateShard(() -> shader));
    private static final RenderType SKY = material("judgment_domain", new ShaderStateShard(() -> skyShader));

    private static RenderType material(String name, ShaderStateShard program) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 4096, false, false, RenderType.CompositeState.builder()
            .setShaderState(program)
            .setDepthTestState(RenderShardAccess.LEQUAL_DEPTH_TEST)
            .setTransparencyState(RenderShardAccess.TRANSLUCENT_TRANSPARENCY)
            .setWriteMaskState(RenderShardAccess.COLOR_WRITE)
            .setCullState(RenderShardAccess.NO_CULL).createCompositeState(false));
    }

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SCREEN_EFFECT = builder.define("judgmentScreenEffect", true);
        UNLEASHED = builder.define("judgmentUnleashed", false);
        DAMAGE_PLAYERS = builder.define("judgmentDamagePlayers", false);
        CONFIG = builder.build();
    }

    private JudgmentVisuals() {}

    @EventBusSubscriber(modid = "shizuku", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), shizuku.prefix("judgment_rift"),
                DefaultVertexFormat.POSITION_TEX), loaded -> shader = loaded);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), shizuku.prefix("judgment_domain"),
                DefaultVertexFormat.POSITION_TEX), loaded -> skyShader = loaded);
        }
    }

    private static void changeLevel(ClientLevel level) {
        if (currentLevel != level) {
            casts.clear();
            if (domainMesh != null) {
                domainMesh.close();
                domainMesh = null;
            }
            currentLevel = level;
        }
    }

    public static void play(JudgmentImpactPacket cast) {
        changeLevel(Minecraft.getInstance().level);
        if (currentLevel != null) {
            Cast previous = casts.get(cast.caster());
            boolean same = previous != null && previous.packet.startTick() == cast.startTick();
            Vec3 center = same ? previous.center : cast.origin();
            Vec3 landing = cast.phaseTick() == Ymsa.VERDICT_TICK ? swordLanding(cast) : null;
            casts.put(cast.caster(), new Cast(cast, center, currentLevel.getGameTime() - cast.phaseTick(), landing));
        }
    }

    private static Vec3 swordLanding(JudgmentImpactPacket cast) {
        Vec3 target = cast.origin().add(Vec3.directionFromRotation(0, cast.yaw()).scale(30));
        BlockHitResult ground = currentLevel.clip(new ClipContext(target.add(0, DROP_HEIGHT, 0),
            new Vec3(target.x, currentLevel.getMinBuildHeight() - 1, target.z),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
        return ground.getType() == HitResult.Type.BLOCK ? ground.getLocation() : target.add(0, -1.6, 0);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        changeLevel(Minecraft.getInstance().level);
        if (currentLevel != null && !casts.isEmpty()) {
            long now = currentLevel.getGameTime();
            casts.values().removeIf(cast -> now - cast.localStart > expiry(cast));
        }
    }

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || casts.isEmpty()
                || !SCREEN_EFFECT.get() || currentLevel == null) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        for (Cast active : casts.values()) {
            JudgmentImpactPacket cast = active.packet;
            float tick = currentLevel.getGameTime() - active.localStart + event.getPartialTick();
            float opacity = strength(active, tick);
            if (opacity <= 0) continue;
            drawDomain(pose, active.center.subtract(camera), tick, opacity);
            if (active.landing != null) {
                Vec3 offset = active.landing.subtract(camera);
                pose.pushPose();
                pose.translate(offset.x, offset.y, offset.z);
                pose.mulPose(Axis.YP.rotationDegrees(-cast.yaw()));
                MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
                drawSword(pose, buffers, tick);
                buffers.endBatch();
                pose.popPose();
                continue;
            }
            if (cast.phaseTick() < Ymsa.STRIKE_TICK || tick >= Ymsa.RIFT_CLOSED_TICK) continue;
            Vec3 center = cast.origin().add(Vec3.directionFromRotation(cast.pitch(), cast.yaw()).scale(5));
            pose.pushPose();
            pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(-cast.yaw()));
            pose.mulPose(Axis.XP.rotationDegrees(cast.pitch()));
            // Local +X is screen-left when the caster faces +Z.
            pose.scale(-5.8F, 3.26F, 1);
            draw(pose.last().pose(), tick);
            pose.popPose();
        }
    }

    private static int expiry(Cast cast) {
        if (cast.packet.phaseTick() == 1) return Ymsa.STRIKE_TICK + 4;
        return cast.packet.phaseTick() == Ymsa.VERDICT_TICK ? Ymsa.END_TICK : Ymsa.VERDICT_TICK + 4;
    }

    private static float strength(Cast cast, float tick) {
        return Mth.clamp(tick / 5, 0, 1) * Mth.clamp((expiry(cast) - tick) / 5, 0, 1);
    }

    private static float immersion(Vec3 eye, float partial) {
        if (casts.isEmpty() || currentLevel == null || !SCREEN_EFFECT.get()) return 0;
        float amount = 0;
        for (Cast cast : casts.values()) {
            float distance = (float) eye.distanceTo(cast.center);
            float inside = Mth.clamp((Ymsa.DOMAIN_RADIUS - distance) / 8, 0, 1);
            float tick = currentLevel.getGameTime() - cast.localStart + partial;
            amount = Math.max(amount, inside * strength(cast, tick));
        }
        return amount;
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.ComputeFogColor event) {
        float amount = immersion(event.getCamera().getPosition(), (float)event.getPartialTick()) * 0.94F;
        if (amount > 0) {
            event.setRed(Mth.lerp(amount, event.getRed(), 0.20F));
            event.setGreen(Mth.lerp(amount, event.getGreen(), 0.012F));
            event.setBlue(Mth.lerp(amount, event.getBlue(), 0.047F));
        }
    }

    @SubscribeEvent
    public static void shade(RenderGuiEvent.Pre event) {
        float amount = immersion(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(), event.getPartialTick());
        if (amount <= 0) return;
        GuiGraphics g = event.getGuiGraphics();
        int red = ((int)(amount * 95) << 24) | 0x360517;
        int dark = ((int)(amount * 138) << 24) | 0x140612;
        g.fillGradient(0, 0, g.guiWidth(), g.guiHeight(), red, dark);
    }

    private static void drawDomain(PoseStack pose, Vec3 offset, float tick, float opacity) {
        if (skyShader == null) return;
        skyShader.safeGetUniform("Atmosphere").set(tick, opacity);
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        pose.scale(Ymsa.DOMAIN_RADIUS, Ymsa.DOMAIN_RADIUS, Ymsa.DOMAIN_RADIUS);
        if (domainMesh == null) domainMesh = buildDomainMesh();
        SKY.setupRenderState();
        domainMesh.bind();
        domainMesh.drawWithShader(new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose()),
            RenderSystem.getProjectionMatrix(), skyShader);
        VertexBuffer.unbind();
        SKY.clearRenderState();
        pose.popPose();
    }

    private static VertexBuffer buildDomainMesh() {
        BufferBuilder mesh = new BufferBuilder(40 * 16 * 4 * DefaultVertexFormat.POSITION_TEX.getVertexSize());
        mesh.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int j = 0; j < 16; j++) {
            for (int i = 0; i < 40; i++) {
                sphereVertex(mesh, i, j);
                sphereVertex(mesh, i+1, j);
                sphereVertex(mesh, i+1, j+1);
                sphereVertex(mesh, i, j+1);
            }
        }
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(mesh.end());
        VertexBuffer.unbind();
        return buffer;
    }

    private static void sphereVertex(VertexConsumer mesh, int i, int j) {
        float u = i / 40F, v = j / 16F;
        float longitude = u * Mth.TWO_PI, latitude = (v - 0.5F) * Mth.PI;
        float radius = Mth.cos(latitude);
        mesh.vertex(radius*Mth.cos(longitude), Mth.sin(latitude), radius*Mth.sin(longitude)).uv(u, v).endVertex();
    }

    public static void renderCut(GuiGraphics graphics, int x, int y, int width, int height, long age) {
        float tick = age / 50F;
        if (tick < 0 || tick >= Ymsa.END_TICK) return;
        graphics.flush();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        if (tick >= Ymsa.VERDICT_TICK) {
            float scale = Math.min(width / 19F, height / 54F);
            pose.translate(x + width * 0.5F, y + height * 0.95F, 60);
            pose.scale(scale, -scale, -scale);
            drawSword(pose, graphics.bufferSource(), tick);
            graphics.flush();
        } else {
            pose.translate(x + width * 0.5F, y + height * 0.5F, 5);
            pose.scale(width * 0.5F, -height * 0.5F, 1);
            draw(pose.last().pose(), tick);
        }
        pose.popPose();
    }

    private static void drawSword(PoseStack pose, MultiBufferSource buffers, float tick) {
        float progress = Mth.clamp((tick - Ymsa.VERDICT_TICK) / (Ymsa.SHEATH_TICK - Ymsa.VERDICT_TICK), 0, 1);
        // Slightly bury the tip of the rotated 16x16 item model when the fall ends.
        pose.translate(0, SWORD_SCALE * 0.57F + DROP_HEIGHT * (1 - progress * progress), 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-135));
        pose.scale(SWORD_SCALE, SWORD_SCALE, SWORD_SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(DIAMOND_SWORD, ItemDisplayContext.NONE,
            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, currentLevel, 0);
    }

    private static void draw(Matrix4f matrix, float tick) {
        if (shader == null || tick < Ymsa.STRIKE_TICK || tick >= Ymsa.RIFT_CLOSED_TICK) return;
        shader.safeGetUniform("Phase").set(tick, (float)Ymsa.STRIKE_TICK, (float)Ymsa.RIFT_CLOSED_TICK);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer quad = buffers.getBuffer(RIFT);
        quad.vertex(matrix, -1, -1, 0).uv(0, 0).endVertex();
        quad.vertex(matrix, 1, -1, 0).uv(1, 0).endVertex();
        quad.vertex(matrix, 1, 1, 0).uv(1, 1).endVertex();
        quad.vertex(matrix, -1, 1, 0).uv(0, 1).endVertex();
        buffers.endBatch(RIFT);
    }
}
