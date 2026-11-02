package com.buwan.shizuku.client;

import com.buwan.shizuku.compat.RenderLevelStageEvent;

/** 合成波全屏后处理：由 Fabric WorldRenderEvents.AFTER_ENTITIES 派发（兼容 AFTER_CUTOUT_BLOCKS 阶段）。 */
public class SynthwaveShaderRenderer {
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            SynthwaveShaderManager.render(event.getPoseStack(), event.getProjectionMatrix());
        }
    }
}
