package com.buwan.shizuku.client;

import com.buwan.shizuku.client.shader.CosmicShaders;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * 宇宙刀的「手持星光」（26.1，1.20.1 原版忠实移植）。
 *
 * <p>原版逻辑（modrinth37 CosmicBladeRenderer + CosmicShaders.renderCosmic）：</p>
 * <ul>
 *   <li>只处理宇宙刀（26.1 事件没有 getStack，用模型贴图名 cosmic.png 等价判定）；</li>
 *   <li>在 {@code *_luminous} 分组事件上，把<b>基础分组</b>（去掉 _luminous 后缀）用
 *       同一个 cosmic RenderType（= 星河圆盘那套：items 图集 + 闪电混合 + 星空 fsh）
 *       tessellate 进去，opacity=1.0、满亮度 15728880；</li>
 *   <li><b>不取消</b>发光层 —— 原版就是「本体材质 + 发光层 + 星空叠加」三层共存；</li>
 *   <li>参数取原版 prepareCosmicRender 非 GUI 路径的值：time=世界刻、yaw=+yRot 弧度、
 *       pitch=-xRot 弧度、externalScale=1.0。</li>
 * </ul>
 *
 * <p>26.1 无 per-draw uniform ⇒ 参数由 {@link CosmicShaders#submitModel} 里那层 wrapper
 * 写进着色器不读的顶点属性槽位（UV0=(time,yaw)、Normal=(pitch,externalScale,0)、
 * Color.a=opacity），与圆盘/斩击/刀光共用同一管线。Face 静态算子的中和也在那一层。</p>
 */
public final class CosmicBladeRenderer {

    /** 手持星光开关（26.1 忠实移植完成，实机确认 ✅）。 */
    public static final boolean ENABLE_COSMIC_HELD_EFFECT = true;

    private CosmicBladeRenderer() {
    }

    public static void register() {
        RenderOverrideEvent.CALLBACK.register(CosmicBladeRenderer::onRenderOverride);
    }

    private static void onRenderOverride(RenderOverrideEvent event) {
        if (!ENABLE_COSMIC_HELD_EFFECT) {
            return;
        }
        String target = event.getTarget();
        if (target == null || !target.endsWith("_luminous")) {
            return;
        }
        // 只处理「宇宙刀」：26.1 事件没有 getStack（渲染走 render state），
        // 宇宙刀的模型贴图 = model/shizuku/cosmic.png，按贴图名等价判定。
        var texture = event.getTexture();
        if (texture == null || !texture.getPath().contains("cosmic")) {
            return;
        }
        WavefrontObject model = event.getModel();
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        if (model == null || poseStack == null || collector == null) {
            return;
        }

        // ⚠️ 只画事件对应的基础分组（blade / blade_damaged）——与 1.20.1 原版逐字一致。
        // wdq.obj 里的 handle(92面) 是「上游从不渲染的遗留组」（LayerMainBlade 的分组字符串
        // 里没有 handle；几何位置/朝向是歪的 = 侧立长三角的来源），画它只会引入垃圾几何。
        // 第一次尝试（只画 blade）看到的「空缺」其实是 Face 静态算子残留的 alpha 洞
        // （中和器已修），当时误诊成「柄没盖到」。
        String baseTarget = target.substring(0, target.length() - "_luminous".length());
        if (!hasGroup(model, baseTarget)) {
            return;
        }

        // 原版 renderCosmic(model, group, pose, buffer, opacity=1.0F)
        CosmicShaders.submitModel(model, baseTarget, poseStack, collector, 1.0F);
    }

    /** 模型里是否存在该分组（OBJ 的分组名列表是公开的）。 */
    private static boolean hasGroup(WavefrontObject model, String name) {
        if (name == null || model.groupObjects == null) {
            return false;
        }
        for (var group : model.groupObjects) {
            if (name.equals(group.name)) {
                return true;
            }
        }
        return false;
    }
}
