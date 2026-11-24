package com.buwan.shizuku.client;

import com.buwan.shizuku.network.JudgmentImpactPacket;
import com.buwan.shizuku.sa.Ymsa;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

/**
 * 「审判」的客户端视觉。
 *
 * <p>26.1 阶段一简化（重要，改动全部集中在绘制层，逻辑语义未变）：</p>
 * <ul>
 *   <li>1.20.1/1.21.1 的域球（自定义 {@code ShaderInstance} + 预上传球面 {@code VertexBuffer}）、
 *       空间裂缝（{@code judgment_rift} 着色器四边形）、世界里的落剑（{@code ItemRenderer#renderStatic}）
 *       全都依赖 26.1 已删除的 API（{@code ShaderInstance} / {@code RenderType} / {@code VertexBuffer} /
 *       {@code BufferUploader} / {@code Tesselator} 直绘）。现在 <b>世界内不再画任何审判几何</b>
 *       （{@link #renderWorld} 变成空实现），只保留「屏幕红染」与「面板里的斩击预览」两处视觉。</li>
 *   <li>屏幕红染/暗角由 {@code fillGradient} 承担（原来也是它，只是不再需要 shader 就绪才画）。</li>
 *   <li>面板预览（{@link #renderCut}）里：裂缝 → 竖直渐变的红色裂痕；落剑 → GUI 物品
 *       （{@code GuiGraphicsExtractor#item}）加旋转/缩放，等价于原来那柄「插进地里」的钻石剑。</li>
 * </ul>
 *
 * <p>时间轴常量统一取 {@link com.buwan.shizuku.sa.Ymsa}（STRIKE_TICK / RIFT_CLOSED_TICK /
 * VERDICT_TICK / DOMAIN_RADIUS / SHEATH_TICK / END_TICK），数值与 1.21.1 完全一致，
 * 本类不再保留任何本地副本。</p>
 */
public final class JudgmentVisuals {
    public static final ForgeConfigSpec.ConfigValue<Boolean> SCREEN_EFFECT;
    public static final ForgeConfigSpec.BooleanValue UNLEASHED;
    public static final ForgeConfigSpec.BooleanValue DAMAGE_PLAYERS;
    public static final ForgeConfigSpec CONFIG;
    /**
     * 审判落剑用的展示物品。
     *
     * <p><b>必须懒加载</b>：26.1 在物品组件绑定完成前构造 {@link ItemStack} 会抛
     * {@code Components not bound yet}（实测崩在 {@code JudgmentVisuals.<clinit>}，
     * 触发点是 mod 初始化时的配置注册 → 类初始化）。这里改成首次绘制时才创建，
     * 并用 try/catch 兜底，绑定失败就退化成 EMPTY（不画剑，但不影响任何逻辑）。</p>
     */
    private static ItemStack diamondSword;

    private static ItemStack swordDisplay() {
        if (diamondSword == null) {
            try {
                diamondSword = new ItemStack(Items.DIAMOND_SWORD);
            } catch (Throwable t) {
                diamondSword = ItemStack.EMPTY;
            }
        }
        return diamondSword;
    }
    /** 落剑判定时从目标点上方多少格往下打射线（与 1.21.1 一致）。 */
    private static final float DROP_HEIGHT = 25;

    private record Cast(JudgmentImpactPacket packet, Vec3 center, long localStart, Vec3 landing) {}
    private static final Map<UUID, Cast> casts = new HashMap<>();
    private static ClientLevel currentLevel;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SCREEN_EFFECT = builder.define("judgmentScreenEffect", true);
        UNLEASHED = builder.define("judgmentUnleashed", false);
        DAMAGE_PLAYERS = builder.define("judgmentDamagePlayers", false);
        CONFIG = builder.build();
    }

    private JudgmentVisuals() {}

    /**
     * 审判 shader 是否可用。
     *
     * <p>26.1 已删除 {@code ShaderInstance}，这里恒为 {@code false}（保留给调用方判断）。
     * 注意屏幕染色已经<b>不再</b>依赖它：降到 {@code fillGradient} 之后即使没有着色器也能画。</p>
     */
    public static boolean shadersReady() {
        return false;
    }

    private static void changeLevel(ClientLevel level) {
        if (currentLevel != level) {
            casts.clear();
            currentLevel = level;
        }
    }

    private static final java.util.Set<String> DEBUG_LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void play(JudgmentImpactPacket cast) {
        changeLevel(Minecraft.getInstance().level);
        if (currentLevel != null) {
            Cast previous = casts.get(cast.caster());
            boolean same = previous != null && previous.packet.startTick() == cast.startTick();
            Vec3 center = same ? previous.center : cast.origin();
            Vec3 landing = cast.phaseTick() == Ymsa.VERDICT_TICK ? swordLanding(cast) : null;
            casts.put(cast.caster(), new Cast(cast, center, currentLevel.getGameTime() - cast.phaseTick(), landing));
            if (DEBUG_LOGGED.add("play|" + cast.phaseTick())) {
                org.slf4j.LoggerFactory.getLogger("shizuku").info(
                        "[judgfx] play phaseTick={} origin={} yaw={} casts={}",
                        cast.phaseTick(), cast.origin(), cast.yaw(), casts.size());
            }
        }
    }

    private static Vec3 swordLanding(JudgmentImpactPacket cast) {
        Vec3 target = cast.origin().add(Vec3.directionFromRotation(0, cast.yaw()).scale(30));
        // 26.1：Level#getMinBuildHeight → LevelHeightAccessor#getMinY（语义相同）。
        BlockHitResult ground = currentLevel.clip(new ClipContext(target.add(0, DROP_HEIGHT, 0),
            new Vec3(target.x, currentLevel.getMinY() - 1, target.z),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
        return ground.getType() == HitResult.Type.BLOCK ? ground.getLocation() : target.add(0, -1.6, 0);
    }

    /**
     * 供 {@link JudgmentPostEffect} 取当前域球参数：{cx, cy, cz, radius, tick, opacity}，
     * 没有活跃施法时返回 {@code null}。
     */
    public static double[] domainState() {
        if (casts.isEmpty() || currentLevel == null || !screenEffect()) {
            return null;
        }
        long now = currentLevel.getGameTime();
        double[] best = null;
        float bestOpacity = 0.0F;
        for (Cast cast : casts.values()) {
            float tick = now - cast.localStart;
            float opacity = strength(cast, tick);
            if (opacity > bestOpacity) {
                bestOpacity = opacity;
                best = new double[]{cast.center.x, cast.center.y, cast.center.z,
                        Ymsa.DOMAIN_RADIUS, tick, opacity};
            }
        }
        return best;
    }

    public static void tick() {
        changeLevel(Minecraft.getInstance().level);
        if (currentLevel != null && !casts.isEmpty()) {
            long now = currentLevel.getGameTime();
            casts.values().removeIf(cast -> now - cast.localStart > expiry(cast));
        }
    }

    /**
     * 世界内绘制入口（26.1 阶段一简化：空实现）。
     *
     * <p>原来在这里画域球、裂缝、落剑；这些全部依赖 26.1 已移除的几何/着色器 API。
     * 保留签名与空实现，是为了让调用点（以及未来可能的重写）不用改结构 —— 少画不会崩。</p>
     */
    public static void renderWorld(PoseStack pose, Vec3 camera, float partialTick) {
        if (casts.isEmpty() || currentLevel == null || !screenEffect()) {
            return;
        }
        long now = currentLevel.getGameTime();
        if (DEBUG_LOGGED.add("render|" + casts.size())) {
            org.slf4j.LoggerFactory.getLogger("shizuku").info("[judgfx] renderWorld casts={}", casts.size());
        }
        for (Cast active : casts.values()) {
            JudgmentImpactPacket cast = active.packet;
            float tick = now - active.localStart + partialTick;
            float opacity = strength(active, tick);
            if (opacity <= 0) {
                continue;
            }
            // 域球改由 `JudgmentPostEffect` 做全屏后处理（世界几何会被天空盖住）
            if (active.landing != null) {
                // TODO 落剑：原版是钻石剑物品模型，26.1 的物品世界渲染需另接（见 PROJECT_MEMORY）
                continue;
            }
            if (cast.phaseTick() < Ymsa.STRIKE_TICK || tick >= Ymsa.RIFT_CLOSED_TICK) {
                continue;
            }
            // 空间裂缝（原版：单位四边形 + judgment_rift 着色器，缩放 (-5.8, 3.26, 1)）
            Vec3 center = cast.origin().add(Vec3.directionFromRotation(cast.pitch(), cast.yaw()).scale(5));
            pose.pushPose();
            pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-cast.yaw()));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(cast.pitch()));
            // 本地 +X 在施法者朝 +Z 时是屏幕左侧（原版注释）
            pose.scale(-5.8F, 3.26F, 1.0F);
            drawRift(pose.last().pose(), tick, opacity);
            pose.popPose();
        }
    }

    // ---------------- 世界内几何（1.20.1 原版忠实移植） ----------------
    //
    // 原版结构：
    //   · 域球 = 单位球面网格（16 纬 × 40 经）+ `judgment_domain`，pose 缩放到 DOMAIN_RADIUS；
    //   · 裂缝 = 单位四边形 (-1,-1)..(1,1) + `judgment_rift`，pose 缩放 (-5.8, 3.26, 1)；
    //   · 落剑 = 钻石剑物品模型（26.1 待接）。
    // 26.1 的 RenderType 只能给常量 uniform，原版两个逐帧 uniform 改由顶点属性 Normal 携带：
    //   domain: Normal = (tick, opacity, 0)
    //   rift  : Normal = (tick, STRIKE_TICK, RIFT_CLOSED_TICK)

    private static final int MAX_LIGHT = 15728880;

    /** 保底可见探针：相机正前方 2 格、2x2 的白色四边形（debugQuads，已验证可见的 RenderType）。 */
    private static void drawProbeQuad(PoseStack pose, Vec3 camera) {
        org.joml.Matrix4f rot = new org.joml.Matrix4f()
                .rotateY((float) Math.toRadians(-camera.y))
                .rotateX((float) Math.toRadians(camera.x));
        org.joml.Vector3f fwd = new org.joml.Vector3f(0.0F, 0.0F, -1.0F).mulPosition(rot);
        org.joml.Vector3f right = new org.joml.Vector3f(1.0F, 0.0F, 0.0F).mulDirection(rot);
        org.joml.Vector3f up = new org.joml.Vector3f(0.0F, 1.0F, 0.0F).mulDirection(rot);
        float dist = 2.0F;
        float half = 1.0F;
        org.joml.Vector3f c = new org.joml.Vector3f(fwd).mul(dist);
        pose.pushPose();
        Matrix4f m = pose.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (float[] corner : corners) {
            float x = c.x + right.x * corner[0] * half + up.x * corner[1] * half;
            float y = c.y + right.y * corner[0] * half + up.y * corner[1] * half;
            float z = c.z + right.z * corner[0] * half + up.z * corner[1] * half;
            buffer.addVertex(m, x, y, z).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads().draw(buffer.buildOrThrow());
        pose.popPose();
    }

    private static boolean screenEffect() {
        try {
            return SCREEN_EFFECT.get();
        } catch (Throwable t) {
            return true;
        }
    }

    private static BufferBuilder beginTriangles() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.ENTITY);
    }

    /** 写一个 ENTITY 格式顶点（Position + Color + UV0 + Overlay + Light + Normal）。 */
    /**
     * 写一个 ENTITY 格式顶点。
     *
     * <p>顶点色在这里不只是颜色：26.1 的 RenderType 给不了逐帧 uniform，原版的两个
     * {@code Atmosphere}/{@code Phase} 就编码进 {@code Color}(0..1 浮点) ——
     * 这是唯一语义明确、不会被归一化/截断搞坏的通道（Normal 是归一化字节，
     * UV1/UV2 实机上不透明度只出来 ~25%）。着色器里再 {@code * 64} 还原。</p>
     */
    private static void vertex(BufferBuilder buffer, Matrix4f m,
                               float x, float y, float z, float u,
                               float pr, float pg, float pb) {
        buffer.addVertex(m, x, y, z)
                .setColor(pr, pg, pb, 1.0F)
                .setUv(u, 0.0F)
                .setUv1(0, 0)
                .setUv2(MAX_LIGHT, MAX_LIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /** 域球：球面参数化与 1.20.1 的 sphereVertex 完全相同（16 纬 × 40 经）。 */
    private static void drawDomain(PoseStack pose, Vec3 offset, float tick, float opacity) {
        pose.pushPose();
        pose.translate((float) offset.x, (float) offset.y, (float) offset.z);
        pose.scale(Ymsa.DOMAIN_RADIUS, Ymsa.DOMAIN_RADIUS, Ymsa.DOMAIN_RADIUS);
        Matrix4f m = pose.last().pose();

        int rings = 16;
        int segments = 40;
        BufferBuilder buffer = beginTriangles();
        for (int j = 0; j < rings; j++) {
            for (int i = 0; i < segments; i++) {
                // 四个角的 (经度比例 u, 纬度比例 v)
                float u0 = i / (float) segments;
                float u1 = (i + 1) / (float) segments;
                float v0 = j / (float) rings;
                float v1 = (j + 1) / (float) rings;
                // 原版顶点顺序： (i,j) (i+1,j) (i+1,j+1) (i,j+1)
                sphereTriangle(buffer, m, u0, v0, u1, v0, u1, v1, tick, opacity);
                sphereTriangle(buffer, m, u0, v0, u1, v1, u0, v1, tick, opacity);
            }
        }
        com.buwan.shizuku.client.shader.JudgmentShaders.domain().draw(buffer.buildOrThrow());
        pose.popPose();
    }

    /** 球面顶点：与原版一致，x=cos(lat)cos(lon), y=sin(lat), z=cos(lat)sin(lon)。 */
    private static void sphereTriangle(BufferBuilder buffer, Matrix4f m,
                                       float ua, float va, float ub, float vb, float uc, float vc,
                                       float tick, float opacity) {
        sphereVertex(buffer, m, ua, va, tick, opacity);
        sphereVertex(buffer, m, ub, vb, tick, opacity);
        sphereVertex(buffer, m, uc, vc, tick, opacity);
    }

    private static void sphereVertex(BufferBuilder buffer, Matrix4f m, float u, float v,
                                     float tick, float opacity) {
        float longitude = u * Mth.TWO_PI;
        float latitude = (v - 0.5F) * Mth.PI;
        float radius = Mth.cos(latitude);
        vertex(buffer, m, radius * Mth.cos(longitude), Mth.sin(latitude), radius * Mth.sin(longitude),
                u, tick / 64.0F, opacity, 0.0F);
    }

    /** 空间裂缝：单位四边形，UV 0..1；Phase = (tick, STRIKE_TICK, RIFT_CLOSED_TICK)。 */
    private static void drawRift(Matrix4f m, float tick, float opacity) {
        BufferBuilder buffer = beginTriangles();
        float t = tick / 64.0F;
        float impact = Ymsa.STRIKE_TICK / 64.0F;
        float lock = Ymsa.RIFT_CLOSED_TICK / 64.0F;
        vertex(buffer, m, -1.0F, -1.0F, 0.0F, 0.0F, t, impact, lock);
        vertex(buffer, m, 1.0F, -1.0F, 0.0F, 1.0F, t, impact, lock);
        vertex(buffer, m, 1.0F, 1.0F, 0.0F, 1.0F, t, impact, lock);
        vertex(buffer, m, -1.0F, -1.0F, 0.0F, 0.0F, t, impact, lock);
        vertex(buffer, m, 1.0F, 1.0F, 0.0F, 1.0F, t, impact, lock);
        vertex(buffer, m, -1.0F, 1.0F, 0.0F, 0.0F, t, impact, lock);
        com.buwan.shizuku.client.shader.JudgmentShaders.rift().draw(buffer.buildOrThrow());
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

    /**
     * 屏幕红染（26.1 阶段一：不再等 shader 就绪，直接用两层半透明 {@code fillGradient} 叠出红雾）。
     */
    public static void shade(GuiGraphicsExtractor g, float partialTick) {
        try {
            // 26.1：Camera#getPosition → Camera#position()。
            float amount = immersion(Minecraft.getInstance().gameRenderer.getMainCamera().position(), partialTick);
            if (amount <= 0) return;
            int red = ((int)(amount * 95) << 24) | 0x360517;
            int dark = ((int)(amount * 138) << 24) | 0x140612;
            g.fillGradient(0, 0, g.guiWidth(), g.guiHeight(), red, dark);
        } catch (Throwable ignored) {
            // HUD 每帧都会调这里：配置未加载/相机未就绪等任何异常都静默跳过，绝不影响游戏。
        }
    }

    /**
     * 面板里的斩击预览（{@link com.buwan.shizuku.client.screen.JudgmentScreen} 的「视觉效果」页签调用）。
     *
     * <p>26.1 阶段一简化：原来的「3D 裂缝四边形」→ 竖直红色渐变的裂痕；
     * 原来的「3D 物品模型 + 透视矩阵」→ {@code GuiGraphicsExtractor#item} 画 GUI 物品，
     * 用 pose 的 translate/rotate/scale 还原「从高处落下、斜插在地」的观感。
     * 任何一步失败都静默跳过（预览区最多是空的，绝不影响界面本身）。</p>
     */
    public static void renderCut(GuiGraphicsExtractor graphics, int x, int y, int width, int height, long age) {
        float tick = age / 50F;
        if (tick < 0 || tick >= Ymsa.END_TICK) return;
        try {
            if (tick >= Ymsa.VERDICT_TICK) {
                renderFallingSword(graphics, x, y, width, height, tick);
            } else {
                renderRift(graphics, x, y, width, height, tick);
            }
        } catch (Exception ignored) {
            // 视觉失败一律静默：面板照常显示。
        }
    }

    /** 裂痕：由深红辉光 + 亮白核心两层竖直渐变组成，随相位淡入淡出。 */
    private static void renderRift(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float tick) {
        float fadeIn = Mth.clamp((tick - Ymsa.STRIKE_TICK) / 3F, 0, 1);
        float fadeOut = Mth.clamp((Ymsa.RIFT_CLOSED_TICK - tick) / 3F, 0, 1);
        float alpha = fadeIn * fadeOut;
        if (alpha <= 0.01F) {
            return;
        }
        int cx = x + width / 2;
        int top = y + 2;
        int bottom = y + height - 2;
        if (bottom <= top) {
            return;
        }
        int glow = argb((int) (alpha * 120), 0xFF3344);
        int glowFade = argb((int) (alpha * 45), 0x7A0F16);
        int core = argb((int) (alpha * 235), 0xFFFFFF);
        int coreFade = argb((int) (alpha * 150), 0xFF9AA8);
        graphics.fillGradient(cx - 4, top, cx + 4, bottom, glow, glowFade);
        graphics.fillGradient(cx - 1, top, cx + 1, bottom, core, coreFade);
    }

    /** 落剑：GUI 物品 + pose 缩放/旋转（替代原来的 3D 静态物品渲染）。 */
    private static void renderFallingSword(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                           float tick) {
        Minecraft minecraft = Minecraft.getInstance();
        // GUI 物品的渲染状态要解析物品模型（需要玩家/世界上下文）；
        // 主菜单里打开本面板时 player/level 为 null，宁可不画这一帧，也不冒 NPE 的风险。
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        float scale = Math.min(width / 19F, height / 54F);
        if (scale <= 0) {
            return;
        }
        float progress = Mth.clamp((tick - Ymsa.VERDICT_TICK) / (Ymsa.SHEATH_TICK - Ymsa.VERDICT_TICK), 0, 1);
        // 起始时从预览区上方落下，落定后停在底部（原来是 3D 世界的 DROP_HEIGHT=25 格）。
        float drop = (1 - progress * progress) * height * 0.45F;
        float cx = x + width * 0.5F;
        float cy = y + height * 0.95F - 8.0F * scale - drop;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(-45));
        pose.scale(scale, scale);
        graphics.item(swordDisplay(), -8, -8);
        pose.popMatrix();
    }

    /** ARGB 组装（alpha 夹到 0~255）。 */
    private static int argb(int alpha, int rgb) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
