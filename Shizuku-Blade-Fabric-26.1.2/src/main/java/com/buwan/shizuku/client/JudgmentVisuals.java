package com.buwan.shizuku.client;

import com.buwan.shizuku.network.JudgmentImpactPacket;
import com.buwan.shizuku.sa.Ymsa;
import com.mojang.blaze3d.vertex.PoseStack;
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
    private static final ItemStack DIAMOND_SWORD = new ItemStack(Items.DIAMOND_SWORD);
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
        // 26.1 阶段一简化：世界内审判几何暂缺。
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
        graphics.item(DIAMOND_SWORD, -8, -8);
        pose.popMatrix();
    }

    /** ARGB 组装（alpha 夹到 0~255）。 */
    private static int argb(int alpha, int rgb) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
