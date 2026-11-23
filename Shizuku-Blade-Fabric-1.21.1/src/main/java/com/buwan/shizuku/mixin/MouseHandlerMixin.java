package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.SummonSwordInput;
import mods.flammpfeil.slashblade.client.SlashBladeKeyMappings;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 鼠标按键的「按下瞬间」钩子。
 *
 * <p>SlashBlade-Refabricated 的输入采样是在 {@code END_CLIENT_TICK} 里**轮询**
 * {@code KEY_SUMMON_BLADE.isDown()}（见 {@code MoveInputHandler.onPlayerPostTick}）。
 * 轮询只看得到「tick 边界那一刻键是否还按着」：一次很快的中键点击（按下与松开落在
 * 同一个 tick 内）会被整个漏掉，表现就是「中键幻影剑时灵时不灵」。</p>
 *
 * <p>Forge/NeoForge 上库走的是平台提供的鼠标事件（{@code InputEvent.MouseButton}），
 * 事件驱动不会丢帧，所以那两个端口一直正常。这里给 Fabric 补上同样的「事件驱动」：
 * 在 {@code MouseHandler.onPress} 的按下分支里立刻把 M_DOWN 补进指令集发出去。</p>
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onPress", at = @At("HEAD"))
    private void shizuku$onMousePress(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) {
            return;
        }
        // matchesMouse 用的是**当前绑定**（玩家可能改绑到别的鼠标键/键盘），键盘绑定会返回 false
        if (!SlashBladeKeyMappings.KEY_SUMMON_BLADE.matchesMouse(button)) {
            return;
        }
        SummonSwordInput.onSummonKeyPressed();
    }
}
