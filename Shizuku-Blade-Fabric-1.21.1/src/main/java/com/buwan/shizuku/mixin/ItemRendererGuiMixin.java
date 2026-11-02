package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.CosmicBladeRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 标记「正在以 GUI 上下文渲染物品」。
 *
 * <p>SlashBlade 的 {@code RenderOverrideEvent} 对世界手持模型和 GUI 里的物品图标都会触发，
 * 事件本身又不带显示上下文（只有 packedLight / enableEffect）。如果我们不加区分地画 cosmic 层，
 * 物品栏/快捷栏里的刀就会用 GUI 矩阵画出一大片星空并随视角闪烁。</p>
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererGuiMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void shizuku$enterItemRender(ItemStack stack, ItemDisplayContext displayContext, boolean leftHand,
                                         PoseStack poseStack, MultiBufferSource buffer, int light, int overlay,
                                         BakedModel model, CallbackInfo ci) {
        CosmicBladeRenderer.enterGuiItemRender(displayContext == ItemDisplayContext.GUI);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void shizuku$exitItemRender(ItemStack stack, ItemDisplayContext displayContext, boolean leftHand,
                                        PoseStack poseStack, MultiBufferSource buffer, int light, int overlay,
                                        BakedModel model, CallbackInfo ci) {
        CosmicBladeRenderer.exitGuiItemRender(displayContext == ItemDisplayContext.GUI);
    }
}
