package com.buwan.shizuku.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * 1.20.1 兼容：1.21.1 的 {@code Tesselator.begin(mode, format)} 返回 BufferBuilder，
 * 1.20.1 需要先 getBuilder() 再 begin()。
 */
public final class TesselatorCompat {
    private TesselatorCompat() {
    }

    public static BufferBuilder begin(VertexFormat.Mode mode, VertexFormat format) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(mode, format);
        return builder;
    }
}
