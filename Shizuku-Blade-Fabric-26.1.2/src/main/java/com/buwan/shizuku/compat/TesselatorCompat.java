package com.buwan.shizuku.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * 顶点缓冲入口的薄封装。
 *
 * <p>1.20.1 需要先 {@code getBuilder()} 再 {@code begin(...)}；1.21.1 直接
 * {@code Tesselator.getInstance().begin(mode, format)} 就返回 BufferBuilder。
 * 本端口（1.21.1 Fabric）用后者，保留这个类只是为了让调用方三端写法一致。</p>
 */
public final class TesselatorCompat {
    private TesselatorCompat() {
    }

    public static BufferBuilder begin(VertexFormat.Mode mode, VertexFormat format) {
        return Tesselator.getInstance().begin(mode, format);
    }
}
