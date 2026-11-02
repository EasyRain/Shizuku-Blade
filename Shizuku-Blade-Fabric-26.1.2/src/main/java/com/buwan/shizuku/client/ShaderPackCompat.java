package com.buwan.shizuku.client;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 光影模组兼容检测：Iris / Oculus 的光影管线会接管世界渲染，
 * 自定义全屏后处理与世界内自定义 RenderType 在管线内部渲染会冲突。
 */
public final class ShaderPackCompat {
    private ShaderPackCompat() {
    }

    /** 当前是否有光影包在生效（Iris/Oculus 已加载且 shaderpack 处于使用中）。 */
    public static boolean isShaderPackActive() {
        FabricLoader loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("iris") && !loader.isModLoaded("oculus")) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object active = apiClass.getMethod("isShaderPackInUse").invoke(api);
            return active instanceof Boolean b && b;
        } catch (Throwable t) {
            // 无法确认时按生效处理，避免污染光影管线
            return true;
        }
    }
}
