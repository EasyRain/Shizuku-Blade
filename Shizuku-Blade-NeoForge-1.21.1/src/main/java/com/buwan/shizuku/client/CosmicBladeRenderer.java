package com.buwan.shizuku.client;

import com.buwan.shizuku.client.shader.CosmicShaders;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public final class CosmicBladeRenderer {
    private CosmicBladeRenderer() {
    }

    @SubscribeEvent
    public static void onRenderOverride(RenderOverrideEvent event) {
        ItemStack stack = event.getStack();
        if (stack != null && stack.getItem() instanceof ItemSlashBlade) {
            String translationKey = BladeStateAccess.of(stack)
                    .map(s -> s.getTranslationKey())
                    .orElse("");
            if (CosmicShaders.COSMIC_BLADE_KEY.equals(translationKey)) {
                String target = event.getTarget();
                if (target != null && target.endsWith("_luminous")) {
                    String baseTarget = target.substring(0, target.length() - "_luminous".length());
                    CosmicShaders.renderCosmic(event.getModel(), baseTarget, event.getPoseStack(), event.getBuffer(), 1.0F);
                }
            }
        }
    }
}
