package com.buwan.shizuku.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 1.20.1 网络包统一接口（Fabric 旧通道 API：按 ResourceLocation 通道名收发）。 */
public interface ShizukuPacket {
    ResourceLocation id();

    void write(FriendlyByteBuf buf);
}
