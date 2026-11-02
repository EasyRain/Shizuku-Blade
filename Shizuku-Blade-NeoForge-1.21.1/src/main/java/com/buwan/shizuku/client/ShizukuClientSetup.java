package com.buwan.shizuku.client;

import com.buwan.shizuku.client.shader.CosmicShaders;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;

@OnlyIn(Dist.CLIENT)
public class ShizukuClientSetup {
    public static void register() {
        NeoForge.EVENT_BUS.register(TimeStopRenderer.getInstance());
        NeoForge.EVENT_BUS.register(TimeStopScreenEffect.getInstance());
        NeoForge.EVENT_BUS.register(VoidPrisonRenderer.getInstance());
        NeoForge.EVENT_BUS.register(SynthwaveShaderRenderer.class);
        // 3.6.0：宇宙刀着色渲染 / 星河领域特效 / 输入监听
        NeoForge.EVENT_BUS.register(CosmicShaders.ForgeEvents.class);
        NeoForge.EVENT_BUS.register(CosmicBladeRenderer.class);
        NeoForge.EVENT_BUS.register(GalaxyFieldRenderer.class);
        NeoForge.EVENT_BUS.register(FieldActivateInput.class);
        // 注意：PhantomSlashInput 是原作者取消左键幻影斩后遗留的代码，原版不注册，这里保持一致
    }
}
