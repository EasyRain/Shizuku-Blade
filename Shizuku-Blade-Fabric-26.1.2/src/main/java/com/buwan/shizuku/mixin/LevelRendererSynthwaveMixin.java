package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.SynthwaveShaderManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.1 合成波后处理挂点：renderLevel 组装当帧 FrameGraph 的末段。
 *
 * <p>选 addLateDebugPass 做 WrapOperation 的原因：builder 是它的方法参数（不用脆弱的 locals 捕获），
 * 且它位于 addMainPass / transparency 链 / clouds / weather 全部之后 —— main 目标此时已含完整世界画面。
 * 世界渲染完 → UI 之前，正是全屏后处理的时机。manager 内部有 once-per-frame 去重与
 * 「SA 未激活 / 光影包激活时跳过」闸门。</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSynthwaveMixin {

    @Shadow
    private LevelTargetBundle targets;

    @WrapOperation(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;addLateDebugPass("
                            + "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;"
                            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;"
                            + "Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Matrix4fc;)V"
            )
    )
    private void shizuku$addSynthwaveAfterLateDebug(LevelRenderer instance, FrameGraphBuilder builder,
                                                    CameraRenderState cameraRenderState, GpuBufferSlice slice,
                                                    Matrix4fc matrix, Operation<Void> original) {
        original.call(instance, builder, cameraRenderState, slice, matrix);
        SynthwaveShaderManager.addToFrame(builder, this.targets);
    }
}
