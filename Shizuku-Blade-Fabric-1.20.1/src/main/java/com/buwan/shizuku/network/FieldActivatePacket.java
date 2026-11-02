package com.buwan.shizuku.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** C2S：Shift + X 切换星河领域（仅 mosmicblade）。 */
public class FieldActivatePacket implements ShizukuPacket {

    public static final ResourceLocation ID = new ResourceLocation("shizuku", "field_activate");

    public FieldActivatePacket() {
    }

    public FieldActivatePacket(FriendlyByteBuf buf) {
    }

    @Override
    public void write(FriendlyByteBuf buf) {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
