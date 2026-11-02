package com.buwan.shizuku.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
        modid = "shizuku",
        value = Dist.CLIENT
)
public class SynthwaveShaderRegister {
    private static final Logger LOGGER = LoggerFactory.getLogger(SynthwaveShaderRegister.class);

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            LOGGER.info("Attempting to register Synthwave shader...");
            ShaderInstance shader = new ShaderInstance(
                    event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("shizuku", "synthwave"),
                    DefaultVertexFormat.POSITION_TEX
            );
            event.registerShader(shader, s -> {
                SynthwaveShaderManager.synthwave = s;
                LOGGER.info("Synthwave shader registered successfully!");
            });
        } catch (Exception e) {
            LOGGER.error("Failed to register Synthwave shader", e);
        }
    }
}
