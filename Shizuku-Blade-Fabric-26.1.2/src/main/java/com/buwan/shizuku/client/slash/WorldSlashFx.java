package com.buwan.shizuku.client.slash;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.buwan.shizuku.compat.RenderLevelStageEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 我们自己的刀光（26.1，世界阶段直绘）—— <b>走实测唯一能出画面的那条路</b>。
 *
 * <h2>为什么不用实体渲染器的 {@code SubmitNodeCollector}</h2>
 * <p>实测（A/B 验证）：把 {@code CosmicFx} 那段「已验证能出画面」的代码强行接到我们自己的刀上
 * （{@code -Dshizuku.slashfx.use_cosmic=true}），**也是一像素都没有**；而
 * {@code EntityDrive}/{@code EntitySlashEffect} 的 {@code submit} 阶段里无论谁的代码都不出像素。
 * 同时「抽搐的幻影剑」（上游 {@code renderOverridedLuminous} 画的 {@code EntityDrive}）却是可见的
 * —— 说明实体 submit 那条通道的可见性完全取决于上游自己那套 RenderType，我们的自绘插不进去。</p>
 *
 * <h2>真正能出画面的路径（本项目三处实机验证过）</h2>
 * <ul>
 *   <li>{@code GalaxyFieldRenderer}（星河领域圆盘）、{@code TimeStopRenderer}（时停球）、
 *       {@code VoidPrisonRenderer}：都在 {@code LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN}
 *       → {@code RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS} 阶段，</li>
 *   <li>用 {@code Tesselator.getInstance().begin(...)} 自己攒顶点，</li>
 *   <li>最后 {@code RenderType.draw(buffer.buildOrThrow())} <b>直接画</b>（不经过 submit collector）。</li>
 * </ul>
 * <p>本类照抄这条路径：{@link RenderTypes#debugQuads()}（{@code POSITION_COLOR}，无贴图）画「外缘亮、
 * 内缘透明」的弧形刀光带。先用无贴图版本保证能看见，贴图（上游 {@code slashdim.png}）后续再加。</p>
 *
 * <p>数据来源：<b>客户端侧已有的实体</b>。{@code DriveRendererMixin} / {@code SlashEffectRendererMixin}
 * 的 extract 段（那一段确实会被调用）把「哪个实体、什么朝向、什么刀色」记进本类的列表（每个实体只记一次），
 * 由客户端 tick 老化、在世界阶段画出来。不需要新增网络包。</p>
 */
public final class WorldSlashFx {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldSlashFx.class);
    private static final boolean DEBUG = Boolean.getBoolean("shizuku.slashfx.debug");

    /** 一道刀光。{@code yaw/pitch} 是施法瞬间的朝向（度），{@code color} 是刀色（ARGB 的 RGB 部分）。 */
    private record Arc(Vec3 center, float yaw, float pitch, int color, long startTick, int life) {}

    private static final List<Arc> ARCS = new CopyOnWriteArrayList<>();
    /** 已经记录过的实体（避免每帧 extract 都记一遍）。 */
    private static final Set<UUID> SEEN = ConcurrentHashMap.newKeySet();

    /** 弧带参数：半径内/外、张角、厚度。 */
    private static final float RADIUS_INNER = 1.15F;
    private static final float RADIUS_OUTER = 2.35F;
    private static final float SPAN_DEGREES = 62.0F;
    private static final int SEGMENTS = 18;

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private static void debugOnce(String key, String format, Object... args) {
        if (DEBUG && LOGGED.add(key)) {
            LOGGER.info("[slashfx] " + format, args);
        }
    }

    private WorldSlashFx() {
    }

    /** 生命周期（tick）：默认 14 tick。 */
    public static final int DEFAULT_LIFE = 14;

    /**
     * 记录一道刀光（由两个 renderer mixin 的 extract 段调用）。
     *
     * @param entity 触发它的实体（只用 UUID 去重）
     */
    public static void note(Entity entity, double x, double y, double z,
                            float yaw, float pitch, int color, int life) {
        if (entity == null || life <= 0) {
            return;
        }
        if (!SEEN.add(entity.getUUID())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ARCS.add(new Arc(new Vec3(x, y, z), yaw, pitch, color, mc.level.getGameTime(), life));
        if (DEBUG) {
            LOGGER.info("[slashfx] world-arc noted at=({},{},{}) yaw={} pitch={} color=#{} life={} total={}",
                    String.format("%.2f", x), String.format("%.2f", y), String.format("%.2f", z),
                    yaw, pitch, Integer.toHexString(color), life, ARCS.size());
        }
    }

    /** 客户端每 tick：老化 + 清理（由 {@code ShizukuFabricClient} 的 END_CLIENT_TICK 调用）。 */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ARCS.clear();
            SEEN.clear();
            return;
        }
        long now = mc.level.getGameTime();
        ARCS.removeIf(arc -> now - arc.startTick() > arc.life());
        if (SEEN.size() > 512) {
            SEEN.clear();   // 兜底：实体 UUID 集合不会无限涨
        }
    }

    /** 世界阶段直绘（由 {@code ShizukuFabricClient} 的 AFTER_TRANSLUCENT_TERRAIN 调用）。 */
    public static void render(RenderLevelStageEvent event) {
        if (DEBUG) {
            debugOnce("render-in", "render() invoked stage={} arcs={}", event.getStage(), ARCS.size());
        }
        if (ARCS.isEmpty()) {
            debugOnce("render-empty", "render() 提前返回：ARCS 为空（note() 没进来，或已被 tick 老化）");
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            debugOnce("render-stage", "render() 提前返回：stage={} 不是 AFTER_TRANSLUCENT_BLOCKS", event.getStage());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            debugOnce("render-nolevel", "render() 提前返回：level/player 为 null");
            return;
        }
        if (Boolean.getBoolean("shizuku.slashfx.probe_quad")) {
            // 白块探针：只验证「这条世界阶段直绘路径能不能出像素」，与刀光数据无关
            drawProbeQuad(event.getPoseStack());
            return;
        }
        Vec3 camera = event.getCamera().position();
        long now = mc.level.getGameTime();
        PoseStack poseStack = event.getPoseStack();

        for (Arc arc : ARCS) {
            float age = now - arc.startTick();
            float progress = Mth.clamp(age / (float) arc.life(), 0.0F, 1.0F);
            // 淡入淡出：前 2 tick 淡入，之后线性淡出
            float alpha = Mth.clamp(age / 2.0F, 0.0F, 1.0F) * (1.0F - progress);
            if (alpha <= 0.02F) {
                continue;
            }
            debugOnce("draw-" + arc.center(), "drawArc alpha={} center=({},{},{}) yaw={} pitch={} color=#{}",
                    String.format("%.3f", alpha),
                    String.format("%.2f", arc.center().x), String.format("%.2f", arc.center().y),
                    String.format("%.2f", arc.center().z), arc.yaw(), arc.pitch(),
                    Integer.toHexString(arc.color()));
            drawArc(poseStack, camera, arc, alpha);
        }
    }

    /** 诊断：在「视线正前方 2 格」画一个 1×1 白块（视图空间，不经世界变换）。 */
    private static void drawProbeQuad(PoseStack poseStack) {
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var matrix = poseStack.last().pose();
        buffer.addVertex(matrix, -0.5F, -0.5F, -2.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        buffer.addVertex(matrix, -0.5F, 0.5F, -2.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        buffer.addVertex(matrix, 0.5F, 0.5F, -2.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        buffer.addVertex(matrix, 0.5F, -0.5F, -2.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        debugOnce("probe-quad", "画白色 1x1 探针四边形（视线正前方 2 格）");
        RenderTypes.debugQuads().draw(buffer.buildOrThrow());
        debugOnce("probe-quad-done", "探针四边形 draw() 完成");
    }

    private static void drawArc(PoseStack poseStack, Vec3 camera, Arc arc, float alpha) {
        // 拔刀剑的 getColorCode() 是 **ABGR**；直接当 ARGB 取 RGB 会把红蓝互换。这里换序成 ARGB 再取 RGB。
        int argb = abgrToArgb(arc.color());
        int rgb = (argb & 0xFFFFFF) == 0 ? 0xFFFFFF : argb & 0xFFFFFF;
        float r = (rgb >> 16 & 0xFF) / 255.0F;
        float g = (rgb >> 8 & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;

        // ⚠️ 逐步骤打点 + 每步 try/catch 打全栈：之前「drawArc 有、draw() 日志没有」说明中途抛了异常
        // 又被上层吞掉。这里把 6 个步骤分别标记，异常一律带堆栈打出来。
        poseStack.pushPose();
        try {
            com.mojang.blaze3d.vertex.MeshData mesh;
            BufferBuilder buffer;
            try {
                poseStack.translate(arc.center().x - camera.x, arc.center().y - camera.y, arc.center().z - camera.z);
                poseStack.mulPose(Axis.YP.rotationDegrees(-arc.yaw()));
                poseStack.mulPose(Axis.XP.rotationDegrees(-arc.pitch()));
                debugOnce("s1-pose", "step=pose ok");
            } catch (Throwable t) {
                fail("step1-pose", t);
                return;
            }
            try {
                // ⚠️ 必须 QUADS：RenderTypes.debugQuads() 的 pipeline 顶点模式就是 QUADS
                // （DEBUG_FILLED_SNIPPET + withCull(false)），喂 TRIANGLES 会被按 4 顶点重新分组
                // = 退化三角形 = 屏幕上什么都没有。TimeStopRenderer 的画球同样用 QUADS。
                buffer = Tesselator.getInstance().begin(
                        VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                debugOnce("s2-begin", "step=begin ok mode=QUADS");
            } catch (Throwable t) {
                fail("step2-begin", t);
                return;
            }
            int written = 0;
            try {
                var matrix = poseStack.last().pose();
                float half = SPAN_DEGREES * 0.5F;
                for (int i = 0; i < SEGMENTS; i++) {
                    float a0 = (float) Math.toRadians(-half + SPAN_DEGREES * i / SEGMENTS);
                    float a1 = (float) Math.toRadians(-half + SPAN_DEGREES * (i + 1) / SEGMENTS);
                    float t0 = (i + 0.5F) / SEGMENTS * 2.0F - 1.0F;
                    float edge = 1.0F - t0 * t0;
                    float outerAlpha = alpha * edge;
                    // 三角形 1: 内0 - 外0 - 外1；三角形 2: 内0 - 外1 - 内1（正反各写一遍 = 双面）
                    written += quad(buffer, matrix, a0, a1, r, g, b, outerAlpha);
                }
                debugOnce("s3-verts", "step=vertices ok n={}", written);
            } catch (Throwable t) {
                fail("step3-vertices", t);
                return;
            }
            try {
                mesh = buffer.buildOrThrow();
                debugOnce("s4-build", "step=build ok (written={})", written);
            } catch (Throwable t) {
                fail("step4-build", t);
                return;
            }
            try {
                debugOnce("s5-draw-before", "step=draw-before rt=debugQuads");
                RenderTypes.debugQuads().draw(mesh);
                debugOnce("s6-draw-after", "step=draw-after OK —— 几何已提交给 GPU");
            } catch (Throwable t) {
                fail("step6-draw", t);
            }
        } finally {
            poseStack.popPose();
        }
    }

    /** 写一个弧段（1 个 QUADS 四边形：内缘透明 → 外缘亮），返回写入的顶点数。 */
    private static int quad(BufferBuilder buffer, org.joml.Matrix4f matrix,
                            float a0, float a1, float r, float g, float b, float outerAlpha) {
        vertex(buffer, matrix, a0, RADIUS_INNER, 0.0F, r, g, b, 0.0F);
        vertex(buffer, matrix, a0, RADIUS_OUTER, 0.0F, r, g, b, outerAlpha);
        vertex(buffer, matrix, a1, RADIUS_OUTER, 0.0F, r, g, b, outerAlpha);
        vertex(buffer, matrix, a1, RADIUS_INNER, 0.0F, r, g, b, 0.0F);
        return 4;
    }

    private static void fail(String step, Throwable t) {
        LOGGER.error("[slashfx] 刀光绘制失败于 {}（这条堆栈就是「看得见日志、看不见像素」的原因）", step, t);
    }

    /** ABGR → ARGB（只换 R/B 两个通道）。 */
    private static int abgrToArgb(int c) {
        int a = c >>> 24 & 0xFF;
        int b = c >> 16 & 0xFF;
        int g = c >> 8 & 0xFF;
        int r = c & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 极坐标 → 本地坐标（弧在 XY 平面上，朝 +Z 前伸一点，避免和脚下的方块深度打架）。 */
    private static void vertex(BufferBuilder buffer, org.joml.Matrix4f matrix, float angle, float radius,
                               float z, float r, float g, float b, float a) {
        buffer.addVertex(matrix,
                        (float) (Math.sin(angle) * radius),
                        (float) (Math.cos(angle) * radius),
                        z + 0.35F)
                .setColor(r, g, b, a);
    }
}
