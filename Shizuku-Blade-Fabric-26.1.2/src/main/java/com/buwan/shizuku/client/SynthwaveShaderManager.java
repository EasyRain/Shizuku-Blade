package com.buwan.shizuku.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 合成波全屏后处理（26.1 降级实现）。
 *
 * <p>1.21.1 用 {@code ShaderInstance} + 自定义 uniform + GL 直接调用绘制全屏后处理；
 * 26.1 移除了这套 API（改由 {@code RenderPipeline} + 资源文件定义），
 * 这里保留对外接口但不再实际绘制（特效不可见，SA 逻辑不受影响）。</p>
 */
public class SynthwaveShaderManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SynthwaveShaderManager.class);
    private static boolean isSynthwaveEnabled;

    /** 26.1 无需在代码里创建 ShaderInstance，保留方法供调用方兼容。 */
    public static Object ensureShader() {
        return null;
    }

    public static void render(PoseStack poseStack, Matrix4f projectionMatrix) {
        // 26.1 渲染管线重构后暂不绘制合成波特效。
    }

    public static void startShader() {
        isSynthwaveEnabled = true;
        LOGGER.info("Synthwave shader STARTED (26.1: visual disabled)");
    }

    public static void stopShader() {
        isSynthwaveEnabled = false;
        LOGGER.info("Synthwave shader STOPPED");
    }

    public static boolean isShaderActive() {
        return isSynthwaveEnabled;
    }
}
