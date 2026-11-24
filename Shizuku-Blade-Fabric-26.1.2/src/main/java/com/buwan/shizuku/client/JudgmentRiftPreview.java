package com.buwan.shizuku.client;

import com.buwan.shizuku.client.shader.JudgmentShaders;
import com.buwan.shizuku.sa.Ymsa;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

/**
 * 「审判」面板预览里的空间裂缝 —— <b>直接跑真实的 {@code judgment_rift} 着色器</b>。
 *
 * <p>背景（2026-11-24 两轮实测反馈）：26.1 的 GUI 是渲染状态提取，跑不了自定义 GLSL。
 * 先试过矩形拼贴（「一条条方块色带」）、再试过 fsh 的 CPU 逐行移植（方向反 + 马赛克 +
 * 预览区尺寸下每帧 9~18ms 太贵）。最终方案 = <b>世界帧通道里把裂缝四边形额外画进一张
 * 离屏 {@link TextureTarget}</b>，GUI 预览直接 blit 它 —— 与世界里的裂缝是<b>同一份 GLSL</b>
 * （世界版 {@code rift()} 混合管线；离屏版 {@code riftOffscreen()} 无混合直写，
 * 让 GUI blit 做唯一一次混合 = 与世界一致的合成链路）、fwidth 抗锯齿、逐帧满速动画、
 * 零 CPU 逐像素开销，即用户要的「世界怎么画预览就怎么画」。</p>
 *
 * <p>流程：</p>
 * <ol>
 *   <li>GUI 阶段 {@link #render}（由 {@code JudgmentVisuals.renderCut} 的裂缝相位调用）：
 *       只登记当前预览 tick + 时间戳，然后 blit 离屏目标的颜色视图（v 翻转，见下）。</li>
 *   <li>{@code JudgmentWorldPass} 的 executes 里调 {@link #renderOffscreen}：若预览新鲜
 *       （&lt;250ms 内被 GUI 登记过），清空离屏目标，把「当前相机旋转的逆」推上全局
 *       模型栈（顶点直接给视图空间），用<b>逆投影</b>把 NDC 四角映射成恰好铺满目标的
 *       四边形（uv 0..1、Color=(tick,STRIKE,LOCK)/64 与世界 drawRift 同编码），
 *       经 {@code JudgmentShaders.rift()} 画完弹栈恢复。</li>
 * </ol>
 *
 * <p><b>分辨率 = 预览区物理像素</b>（GUI 像素 × 缩放，每帧登记、尺寸变化时原地重建）：
 * blit 是 1:1、零重采样 —— 固定 512×288 上采样到 ~1000+ 物理像素的 LINEAR 拉伸正是
 * judgworld6 实测「有点糊」的根因。fwidth 抗锯齿只影响裂缝边缘（有意为之），
 * 内部噪声/星点在 1:1 下与世界观感一致。</p>
 *
 * <p><b>v 翻转</b>：渲染目标按 GL 约定 v=0 在<b>底</b>，GUI 贴图按图像约定 v=0 在<b>顶</b>
 * ⇒ blit 时传 {@code v0=1, v1=0}。u 不翻。斜向裂缝因此从屏幕左下到右上
 * （= 世界里已实测验证的方向）。</p>
 */
final class JudgmentRiftPreview {
    /** 离屏分辨率上下限：下限防退化，上限防病态窗口（预览区物理像素通常 ≤2048）。 */
    private static final int MIN_TEX = 64;
    private static final int MAX_TEX = 2048;
    /** 新鲜窗口：GUI 在这个毫秒数内登记过 = 预览页签可见。 */
    private static final long FRESH_MS = 250L;

    private static TextureTarget offscreen;
    private static GpuSampler sampler;
    private static boolean failed;
    private static volatile float previewTick = -1.0F;
    private static volatile long lastNoteMs = Long.MIN_VALUE;
    /** 预览区的物理像素尺寸（GUI 像素 × 缩放，GUI 侧每帧登记）⇒ 离屏 1:1 blit，零重采样。 */
    private static volatile int wantW;
    private static volatile int wantH;

    private JudgmentRiftPreview() {
    }

    /** GUI 侧入口：登记预览 tick 与目标物理尺寸，并 blit 离屏结果（裂缝相位由调用方保证）。 */
    static void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float tick) {
        previewTick = tick;
        lastNoteMs = Util.getMillis();
        int scale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        wantW = Math.min(MAX_TEX, Math.max(MIN_TEX, width * scale));
        wantH = Math.min(MAX_TEX, Math.max(MIN_TEX, height * scale));
        if (tick < Ymsa.STRIKE_TICK || tick >= Ymsa.RIFT_CLOSED_TICK
                || width <= 1 || height <= 1 || offscreen == null) {
            return;
        }
        GpuTextureView view = offscreen.getColorTextureView();
        if (view == null) {
            return;
        }
        if (sampler == null) {
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
        // v 翻转：渲染目标 v=0 在底（GL 约定），GUI 贴图 v=0 在顶
        graphics.blit(view, sampler, x, y, x + width, y + height,
                0.0F, 1.0F, 1.0F, 0.0F);
    }

    /** 预览页签是否仍新鲜（{@code JudgmentWorldPass} 据此决定要不要建帧通道）。 */
    static boolean isActive() {
        return lastNoteMs != Long.MIN_VALUE && Util.getMillis() - lastNoteMs < FRESH_MS;
    }

    /**
     * 世界帧通道里调用（渲染线程）：用真着色器把预览裂缝画进离屏目标。
     *
     * @param projection 当前相机的投影矩阵（用它的逆把 NDC 四角铺满整个目标，
     *                   不碰全局投影状态）
     */
    static void renderOffscreen(Matrix4fc projection) {
        if (failed || !isActive() || projection == null) {
            return;
        }
        float tick = previewTick;
        if (tick < Ymsa.STRIKE_TICK || tick >= Ymsa.RIFT_CLOSED_TICK) {
            return;
        }
        try {
            if (offscreen == null) {
                offscreen = new TextureTarget("shizuku_rift_preview", wantW, wantH, true);
            } else if (offscreen.width != wantW || offscreen.height != wantH) {
                // 预览区尺寸/缩放变了（窗口缩放、面板重开）⇒ 原地重建（渲染线程上合法）
                offscreen.resize(wantW, wantH);
                // 尺寸变化时清掉失败标记：上一轮的失败可能只是 resize 竞态，
                // 否则一次瞬态错误会让预览整个会话都看不到
                failed = false;
            }
            GpuTexture color = offscreen.getColorTexture();
            GpuTexture depth = offscreen.getDepthTexture();
            if (color == null || depth == null) {
                return;
            }
            // 清屏：颜色全透明（裂缝 fsh 对透明底做 srcalpha 混合），深度 1
            RenderSystem.getDevice().createCommandEncoder()
                    .clearColorAndDepthTextures(color, 0, depth, 1.0);
            // 模型视图 → 恒等：推当前相机旋转的逆，顶点直接按视图空间坐标给
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            modelView.mul(new Matrix4f(modelView).invert());
            RenderSystem.outputColorTextureOverride = offscreen.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = offscreen.getDepthTextureView();
            try {
                drawQuad(new Matrix4f(projection).invert(), tick);
            } finally {
                RenderSystem.outputColorTextureOverride = null;
                RenderSystem.outputDepthTextureOverride = null;
                modelView.popMatrix();
            }
        } catch (Throwable t) {
            failed = true; // 只试一次：预览缺图不致命，面板照常
        }
    }

    /**
     * 铺满目标的裂缝四边形（两个三角形；uv 0..1；Color=(tick,STRIKE,LOCK)/64 同世界编码）。
     * 用<b>无混合</b>的离屏管线直写（fsh 输出直排 alpha；GUI blit 时才做唯一一次混合），
     * 避免透明底上混合写导致 a² 预乘、GUI 再混合变 rgb·a³ 的发淡（见 JudgmentShaders 注释）。
     */
    private static void drawQuad(Matrix4f invProj, float tick) {
        float t = tick / 64.0F;
        float impact = Ymsa.STRIKE_TICK / 64.0F;
        float lock = Ymsa.RIFT_CLOSED_TICK / 64.0F;
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.ENTITY);
        vertex(buffer, invProj, -1.0F, -1.0F, 0.0F, 0.0F, 0.0F, t, impact, lock);
        vertex(buffer, invProj, 1.0F, -1.0F, 0.0F, 1.0F, 0.0F, t, impact, lock);
        vertex(buffer, invProj, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, t, impact, lock);
        vertex(buffer, invProj, -1.0F, -1.0F, 0.0F, 0.0F, 0.0F, t, impact, lock);
        vertex(buffer, invProj, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, t, impact, lock);
        vertex(buffer, invProj, -1.0F, 1.0F, 0.0F, 0.0F, 1.0F, t, impact, lock);
        JudgmentShaders.riftOffscreen().draw(buffer.buildOrThrow());
    }

    /** 单顶点：NDC 角经逆投影得到视图空间坐标（ModelViewMat 已是恒等 ⇒ ProjMat*pos 还原回该 NDC 角）。 */
    private static void vertex(BufferBuilder buffer, Matrix4f invProj,
                               float ndcX, float ndcY, float ndcZ,
                               float u, float v, float r, float g, float b) {
        Vector4f pos = new Vector4f(ndcX, ndcY, ndcZ, 1.0F);
        invProj.transform(pos);
        pos.div(pos.w);
        buffer.addVertex(pos.x, pos.y, pos.z)
                .setColor(r, g, b, 1.0F)
                .setUv(u, v)
                .setUv1(0, 0)
                .setUv2(LightCoordsUtil.FULL_BRIGHT, LightCoordsUtil.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
