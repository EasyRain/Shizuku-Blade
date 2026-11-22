package com.buwan.shizuku.mixin;

import com.buwan.shizuku.sa.Ymsa;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务端 tick 末尾与停机时做清理（清空血量缓存、区域状态、黑名单等），
 * 防止长时间运行后静态集合无限增长。
 */
@Mixin(MinecraftServer.class)
public class ServerMaintenanceMixin {
    @Inject(method = "tickServer", at = @At("RETURN"), require = 0)
    private void ymsa$serverTickEnd(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        Ymsa.handleServerTickEnd((MinecraftServer)(Object)this);
    }

    @Inject(method = "stopServer", at = @At("HEAD"), require = 0)
    private void ymsa$serverStop(CallbackInfo ci) {
        Ymsa.clearAllSAState();
    }
}
