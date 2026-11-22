package com.buwan.shizuku.client.shader;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderStateShard.CullStateShard;
import net.minecraft.client.renderer.RenderStateShard.DepthTestStateShard;
import net.minecraft.client.renderer.RenderStateShard.TransparencyStateShard;
import net.minecraft.client.renderer.RenderStateShard.WriteMaskStateShard;

/**
 * 一个小技巧：继承 RenderStateShard 后即可访问它那些 protected 的内部类
 * （TextureStateShard / DepthTestStateShard 等），把它们暴露成公开常量，
 * 供 CosmicShaders 组装 RenderType 时引用，避免写一堆反射。
 */
public final class RenderShardAccess extends RenderStateShard {
    public static final DepthTestStateShard LEQUAL_DEPTH_TEST = RenderStateShard.LEQUAL_DEPTH_TEST;
    public static final TransparencyStateShard LIGHTNING_TRANSPARENCY = RenderStateShard.LIGHTNING_TRANSPARENCY;
    public static final TransparencyStateShard TRANSLUCENT_TRANSPARENCY = RenderStateShard.TRANSLUCENT_TRANSPARENCY;
    public static final CullStateShard NO_CULL = RenderStateShard.NO_CULL;
    public static final WriteMaskStateShard COLOR_WRITE = RenderStateShard.COLOR_WRITE;

    private RenderShardAccess(String name, Runnable setupState, Runnable clearState) {
        super(name, setupState, clearState);
        throw new AssertionError();
    }
}
