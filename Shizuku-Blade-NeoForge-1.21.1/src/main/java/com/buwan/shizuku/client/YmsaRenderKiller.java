package com.buwan.shizuku.client;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 3.5.0 的 YmsaRenderKiller：把指定实体类型的渲染器替换为 NoopRenderer，
 * 即使残留实体被遍历到也不会被绘制（1.21.1 用公共 API 实现，无需 mixin）。
 */
@OnlyIn(Dist.CLIENT)
public class YmsaRenderKiller {

    private YmsaRenderKiller() {
    }

    public static void killEntity(Entity entity) {
        try {
            EntityRenderers.register(entity.getType(), NoopRenderer::new);
        } catch (Exception ignored) {
        }
    }
}
