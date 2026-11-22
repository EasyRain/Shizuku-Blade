package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.CosmicBladeRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 判定「当前正在画 GUI 里的物品图标」，给 cosmic 星光层切换成固定视角。
 *
 * <p>为什么需要这个：原来只看 {@code ScreenEvent.Render.Pre/Post}（{@link CosmicShaders#inventoryRender}），
 * 但**快捷栏根本不在任何 Screen 里**——它是 HUD 的一部分直接画的，
 * 于是快捷栏里的刀会走「世界里手持」的参数（yaw/pitch 取玩家视角），
 * 表现为图标里的星星跟着视角闪。</p>
 *
 * <p>{@code ItemDisplayContext.GUI} 才是原版「这是 GUI 图标」的精确信号：
 * 快捷栏、背包格子、JEI 的物品全走 GUI；地面掉落物（GROUND）、展示框（FIXED）、手持（FIRST/THIRD_PERSON）
 * 都不走。用层数而不是布尔量，是因为物品渲染可能嵌套（如物品里的 BlockEntity 渲染）。</p>
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererGuiMixin {

    @Unique
    private static final String SHIZUKU$RENDER =
            "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Z"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II"
            + "Lnet/minecraft/client/resources/model/BakedModel;)V";

    @Inject(method = SHIZUKU$RENDER, at = @At("HEAD"), require = 0)
    private void shizuku$enterGuiItem(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                            PoseStack poseStack, MultiBufferSource buffer, int light, int overlay,
                                            BakedModel model, CallbackInfo ci) {
        CosmicBladeRenderer.enterGuiItemRender(context == ItemDisplayContext.GUI);
    }

    @Inject(method = SHIZUKU$RENDER, at = @At("RETURN"), require = 0)
    private void shizuku$exitGuiItem(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                            PoseStack poseStack, MultiBufferSource buffer, int light, int overlay,
                                            BakedModel model, CallbackInfo ci) {
        CosmicBladeRenderer.exitGuiItemRender(context == ItemDisplayContext.GUI);
    }
}
