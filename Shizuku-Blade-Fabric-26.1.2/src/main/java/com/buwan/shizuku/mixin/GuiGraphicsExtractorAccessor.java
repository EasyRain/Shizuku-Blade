package com.buwan.shizuku.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@code GuiGraphicsExtractor#deferredTooltip}。
 *
 * <p>26.1 的 tooltip 是延迟到帧末绘制的（{@code setTooltipForNextFrame} 只是把绘制动作存进这个字段），
 * 所以不会受容器界面内 scissor 的影响。我们替换 tooltip 时必须同样延迟，
 * 否则立即绘制会被容器遗留的裁剪区切掉（表现为边框缺一半、四角不一致）。</p>
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {

    @Accessor("deferredTooltip")
    void shizuku$setDeferredTooltip(Runnable runnable);
}
