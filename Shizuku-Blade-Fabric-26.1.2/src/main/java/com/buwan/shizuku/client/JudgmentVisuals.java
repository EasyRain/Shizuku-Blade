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
        // 26.1：Level#getMinBuildHeight → LevelHeightAccessor#getMinY（语义相同）。
        BlockHitResult ground = currentLevel.clip(new ClipContext(target.add(0, DROP_HEIGHT, 0),
            new Vec3(target.x, currentLevel.getMinY() - 1, target.z),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
        return ground.getType() == HitResult.Type.BLOCK ? ground.getLocation() : target.add(0, -1.6, 0);
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
        if (casts.isEmpty() || currentLevel == null) {
            return;
        }
        long now = currentLevel.getGameTime();
        for (Cast cast : casts.values()) {
            float tick = now - cast.localStart + partialTick;
            float opacity = strength(cast, tick);
            if (opacity <= 0.001F) {
                continue;
            }
            // 域球：原版是「站在球里」的暗红雾罩
            drawDomainSphere(pose, cast.center.subtract(camera), opacity);
            if (cast.landing != null) {
                // 第三段：落剑光柱 + 地面冲击环
                drawSwordBeam(pose, cast.landing.subtract(camera), tick, opacity);
            } else if (cast.packet.phaseTick() >= Ymsa.STRIKE_TICK && tick < Ymsa.RIFT_CLOSED_TICK) {
                // 第二段：空间裂缝（十字交叉的竖直发光面）
                drawRift(pose, cast, camera, opacity);
            }
        }
    }

    // ---------------- 世界内几何 ----------------
    //
    // 26.1 只有一条**实测能出画面**的自绘路径：世界阶段 `Tesselator.begin(QUADS, POSITION_COLOR)`
    // + `RenderTypes.debugQuads().draw(buffer.buildOrThrow())`（时停球 / 星河圆盘就是这条路）。
    // 实体 renderer 里的 `SubmitNodeCollector.submitCustomGeometry()` 对本项目零像素，别再走。

    /** 一次性画完一批四边形的工具：所有顶点都写进同一个 buffer，最后统一 draw。 */
    private interface QuadSink {
        void quad(Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                  float x2, float y2, float z2, float x3, float y3, float z3,
                  float r, float g, float b, float a);
    }

    private static BufferBuilder beginQuads() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void endQuads(BufferBuilder buffer) {
        net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads().draw(buffer.buildOrThrow());
    }

    private static void quad(BufferBuilder buffer, Matrix4f m,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        buffer.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
    }

    /** 域球：低透明度的暗红球壳（顶点按纬度做一点渐变，避免死板）。 */
    private static void drawDomainSphere(PoseStack pose, Vec3 offset, float opacity) {
        pose.pushPose();
        pose.translate((float) offset.x, (float) offset.y, (float) offset.z);
        Matrix4f matrix = pose.last().pose();
        float radius = Ymsa.DOMAIN_RADIUS;
        int segments = 24;
        int rings = 14;
        float baseAlpha = opacity * 0.06F;
        BufferBuilder buffer = beginQuads();
        for (int i = 0; i < rings; i++) {
            double lat0 = Math.PI * (-0.5 + (double) i / (double) rings);
            double lat1 = Math.PI * (-0.5 + (double) (i + 1) / (double) rings);
            double y0 = Math.sin(lat0) * radius;
            double y1 = Math.sin(lat1) * radius;
            double r0 = Math.cos(lat0) * radius;
            double r1 = Math.cos(lat1) * radius;
            float edge = (float) (1.0 - Math.abs(Math.cos(lat0)));
            float alpha = baseAlpha * (0.5F + edge * 0.5F);
            for (int j = 0; j < segments; j++) {
                double lng0 = (Math.PI * 2) * (double) j / (double) segments;
                double lng1 = (Math.PI * 2) * (double) (j + 1) / (double) segments;
                float x00 = (float) (Math.cos(lng0) * r0);
                float z00 = (float) (Math.sin(lng0) * r0);
                float x01 = (float) (Math.cos(lng1) * r0);
                float z01 = (float) (Math.sin(lng1) * r0);
                float x10 = (float) (Math.cos(lng0) * r1);
                float z10 = (float) (Math.sin(lng0) * r1);
                float x11 = (float) (Math.cos(lng1) * r1);
                float z11 = (float) (Math.sin(lng1) * r1);
                quad(buffer, matrix, x00, (float) y0, z00, x01, (float) y0, z01,
                        x11, (float) y1, z11, x10, (float) y1, z10, 0.62F, 0.07F, 0.12F, alpha);
            }
        }
        endQuads(buffer);
        // 经纬线框：让球面有「包裹感/深度」，否则整屏就是一片均匀红雾
        drawSphereGrid(pose, radius, 0.85F, 0.18F, 0.22F, opacity * 0.35F);
        pose.popPose();
    }

    /** 经纬线框球（照抄 TimeStopRenderer#drawGrid：26.1 的 linesTranslucent 管线，带半透明混合）。 */
    private static void drawSphereGrid(PoseStack poseStack, float radius, float r, float g, float b, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
        PoseStack.Pose pose = poseStack.last();
        float lineWidth = 1.5F;

        // 纬线：只画三条（-45°/0°/+45°），否则 128 半径的球会糊成一片
        double[] latitudes = {-Math.PI / 4, 0.0, Math.PI / 4};
        int segments = 48;
        for (double lat : latitudes) {
            double y = Math.sin(lat) * radius;
            double ringRadius = Math.cos(lat) * radius;
            for (int i = 0; i < segments; i++) {
                double a0 = (Math.PI * 2) * (double) i / (double) segments;
                double a1 = (Math.PI * 2) * (double) (i + 1) / (double) segments;
                line(buffer, pose, lineWidth,
                        Math.cos(a0) * ringRadius, y, Math.sin(a0) * ringRadius,
                        Math.cos(a1) * ringRadius, y, Math.sin(a1) * ringRadius,
                        r, g, b, alpha);
            }
        }

        // 经线：8 条，alpha 减半
        int meridians = 8;
        int rings = 24;
        for (int m = 0; m < meridians; m++) {
            double lng = (Math.PI * 2) * (double) m / (double) meridians;
            double cl = Math.cos(lng) * radius;
            double sl = Math.sin(lng) * radius;
            for (int i = 0; i < rings; i++) {
                double lat0 = Math.PI * (-0.5 + (double) i / (double) rings);
                double lat1 = Math.PI * (-0.5 + (double) (i + 1) / (double) rings);
                line(buffer, pose, lineWidth,
                        cl * Math.cos(lat0), Math.sin(lat0) * radius, sl * Math.cos(lat0),
                        cl * Math.cos(lat1), Math.sin(lat1) * radius, sl * Math.cos(lat1),
                        r, g, b, alpha * 0.5F);
            }
        }
        net.minecraft.client.renderer.rendertype.RenderTypes.linesTranslucent().draw(buffer.buildOrThrow());
    }

    /** 画一条线段（两端点共用同一个归一化方向作 Normal，与上游 ShapeRenderer 一致）。 */
    private static void line(BufferBuilder buffer, PoseStack.Pose pose, float lineWidth,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             float r, float g, float b, float a) {
        org.joml.Vector3f dir = new org.joml.Vector3f(
                (float) (x1 - x0), (float) (y1 - y0), (float) (z1 - z0)).normalize();
        buffer.addVertex(pose, (float) x0, (float) y0, (float) z0)
                .setColor(r, g, b, a).setNormal(pose, dir).setLineWidth(lineWidth);
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(r, g, b, a).setNormal(pose, dir).setLineWidth(lineWidth);
    }

    /** 空间裂缝：以落剑方向为正面的两个竖直发光面（十字交叉），随施法者朝向旋转。 */
    private static void drawRift(PoseStack pose, Cast cast, Vec3 camera, float opacity) {
        Vec3 center = cast.packet.origin().add(Vec3.directionFromRotation(0, cast.packet.yaw()).scale(5));
        pose.pushPose();
        pose.translate((float) (center.x - camera.x), (float) (center.y - camera.y), (float) (center.z - camera.z));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-cast.packet.yaw()));
        Matrix4f m = pose.last().pose();
        float w = 0.8F;
        float h = 5.2F;
        float a1 = opacity * 0.55F;
        float a2 = opacity * 0.35F;
        BufferBuilder buffer = beginQuads();
        // 面向 Z 方向的裂面
        quad(buffer, m, -w, -h, 0.0F, w, -h, 0.0F, w, h, 0.0F, -w, h, 0.0F, 0.95F, 0.16F, 0.24F, a1);
        // 交叉 90° 的裂面
        quad(buffer, m, 0.0F, -h, -w * 0.6F, 0.0F, -h, w * 0.6F, 0.0F, h, w * 0.6F, 0.0F, h, -w * 0.6F,
                1.0F, 0.35F, 0.42F, a2);
        endQuads(buffer);
        pose.popPose();
    }

    /** 第三段落剑：竖直光柱（四个侧面）+ 地面冲击环。 */
    private static void drawSwordBeam(PoseStack pose, Vec3 offset, float tick, float opacity) {
        pose.pushPose();
        pose.translate((float) offset.x, (float) offset.y, (float) offset.z);
        Matrix4f m = pose.last().pose();
        float beamRadius = 0.38F;
        // 光柱从地面拔到 7 格高，落剑瞬间最亮
        float height = 7.0F * Mth.clamp(tick / (float) Ymsa.END_TICK, 0.55F, 1.0F);
        float alpha = opacity * 0.32F;
        BufferBuilder buffer = beginQuads();
        // 四个侧面（每面 2 个三角 → 用四边形）
        float[][] sides = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (float[] side : sides) {
            float nx = side[0] * beamRadius;
            float nz = side[1] * beamRadius;
            float ox = side[1] * beamRadius;
            float oz = side[0] * beamRadius;
            quad(buffer, m,
                    nx - ox, 0.0F, nz - oz, nx + ox, 0.0F, nz + oz,
                    nx + ox, height, nz + oz, nx - ox, height, nz - oz,
                    1.0F, 0.72F, 0.78F, alpha);
        }
        // 地面冲击环（16 段四边形）
        float inner = 1.2F;
        float outer = 2.6F * Mth.clamp(tick / (float) Ymsa.END_TICK, 0.3F, 1.0F);
        int seg = 16;
        float ringAlpha = opacity * 0.28F;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            float x0 = (float) (Math.cos(a0) * inner);
            float z0 = (float) (Math.sin(a0) * inner);
            float x1 = (float) (Math.cos(a1) * inner);
            float z1 = (float) (Math.sin(a1) * inner);
            float x2 = (float) (Math.cos(a1) * outer);
            float z2 = (float) (Math.sin(a1) * outer);
            float x3 = (float) (Math.cos(a0) * outer);
            float z3 = (float) (Math.sin(a0) * outer);
            quad(buffer, m, x0, 0.02F, z0, x1, 0.02F, z1, x2, 0.02F, z2, x3, 0.02F, z3,
                    0.95F, 0.22F, 0.3F, ringAlpha);
        }
        endQuads(buffer);
        pose.popPose();
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
