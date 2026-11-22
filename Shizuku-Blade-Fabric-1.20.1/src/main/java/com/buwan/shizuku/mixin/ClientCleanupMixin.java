package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.YmsaClientErase;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端退出世界 / 关闭游戏时清理本 mod 的静态状态（区域列表、渲染杀手、
 * 血量缓存等），防止换存档后残留脏数据。
 */
@Mixin(Minecraft.class)
public class ClientCleanupMixin {
    @Inject(method = "clearLevel()V", at = @At("HEAD"), require = 0)
    private void ymsa$clearLevel(CallbackInfo ci) {
        YmsaClientErase.clear();
    }

    @Inject(method = "stop", at = @At("HEAD"), require = 0)
    private void ymsa$stop(CallbackInfo ci) {
        YmsaClientErase.clear();
    }
}
