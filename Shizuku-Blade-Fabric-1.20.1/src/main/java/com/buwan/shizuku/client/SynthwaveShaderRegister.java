package com.buwan.shizuku.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在 RegisterShadersEvent 里注册 synthwave 的 ShaderInstance（读取 shaders/post/synthwave.json）。
 */
@EventBusSubscriber(bus = Bus.MOD, value = Dist.CLIENT)
public class SynthwaveShaderRegister {
    private static final Logger LOGGER = LoggerFactory.getLogger(SynthwaveShaderRegister.class);

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            LOGGER.info("Attempting to register Synthwave shader...");
            ShaderInstance shader = new ShaderInstance(
                event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("shizuku", "synthwave"), DefaultVertexFormat.POSITION
            );
            event.registerShader(shader, s -> {
                SynthwaveShaderManager.synthwave = s;
                LOGGER.info("Synthwave shader registered successfully!");
            });
        } catch (Exception var2) {
            LOGGER.error("Failed to register Synthwave shader", var2);
            LOGGER.debug("unexpected error", var2);
        }
    }
}
