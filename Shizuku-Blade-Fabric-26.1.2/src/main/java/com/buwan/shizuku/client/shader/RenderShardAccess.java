package com.buwan.shizuku.client.shader;

/**
 * 26.1 阶段一：着色器内部状态访问的占位实现。
 *
 * <p>1.20.1/1.21.1 时这类的做法是「继承 {@code RenderStateShard} 后即可访问它那些 protected 的内部类」
 * （{@code TextureStateShard} / {@code DepthTestStateShard} / {@code CullStateShard} 等），
 * 把它们暴露成公开常量，供 {@code CosmicShaders} / {@code JudgmentVisuals} 组装 {@code RenderType} 时引用，
 * 避免写一堆反射。</p>
 *
 * <p>26.1 已经把 {@code RenderStateShard}、{@code RenderType} 整个换成
 * {@code RenderPipeline} + {@code SubmitNodeCollector} 体系，{@code ShaderInstance} 也被删除，
 * 因此「读着色器内部状态」这条路彻底没有了。<b>但类保留</b>（可能还有引用方），
 * 对外方法一律降级成「恒返回 0 / 空」的空实现：调用方拿到「无效值」自然会跳过绘制，
 * 绝不抛异常、绝不影响服务端逻辑。</p>
 */
public final class RenderShardAccess {

    private RenderShardAccess() {
    }

    /**
     * 是否还能读到着色器内部状态。
     *
     * <p>26.1 恒为 {@code false}（没有 {@code ShaderInstance}/{@code RenderStateShard}）。</p>
     */
    public static boolean available() {
        return false;
    }

    /**
     * 当前着色器程序 id。
     *
     * <p>26.1 恒返回 {@code 0}（0 = 无程序）。</p>
     */
    public static int programId() {
        return 0;
    }

    /**
     * uniform 位置查询。
     *
     * <p>26.1 恒返回 {@code -1}（-1 = 无效位置），调用方据此跳过 {@code glUniform*} 调用。</p>
     */
    public static int uniformLocation(String name) {
        return -1;
    }
}
