package com.buwan.shizuku.mixin;

import com.buwan.shizuku.sa.UnleashedJudgment;
import com.buwan.shizuku.sa.Ymsa;
import java.util.EventObject;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both supported releases request additional copies from the boss's health getters. */
@Pseudo
@Mixin(targets = {"lIllIlIIllIIllI.IlIIIlIlllIllIll.lIIIIllIIIIIIIl.IllIIIIIIIllIIIl.IIlIlIlllllIIII",
    "net.wzz.chaoswither.utils.ChaosUtil"}, remap = false)
public abstract class JudgmentDefenseMixin {
    // This version fixes every spawn to (0, -58, 0) and registers it repeatedly on both sides.
    @Inject(method = "mOvhJ9N5dMnnb4diEzZX678498", at = @At("HEAD"), cancellable = true, require = 0)
    private static void shizuku$spawnOnce(Level level, CallbackInfo callback) {
        shizuku$spawnCovered(level, callback);
    }

    @Inject(method = "summonChaosWither(Lnet/minecraft/world/level/Level;Z)Ljava/util/EventObject;",
        at = @At("HEAD"), cancellable = true, require = 0)
    private static void shizuku$spawnOnce(Level level, boolean announce, CallbackInfoReturnable<EventObject> result) {
        shizuku$spawnCovered(level, result);
    }

    @org.spongepowered.asm.mixin.Unique
    private static void shizuku$spawnCovered(Level level, CallbackInfo callback) {
        Vec3 pos = new Vec3(0, -58, 0);
        if (!UnleashedJudgment.coversChaosPosition(level, pos)) return;
        callback.cancel();
        if (level instanceof ServerLevel server) server.getServer().execute(() -> {
            if (Ymsa.blocksChaosSpawn(server, pos)) return;
            var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("chaoswither", "chaos_wither"));
            if (type == null) return;
            Entity boss = type.create(server);
            if (boss != null) {
                boss.setPos(pos);
                server.addFreshEntity(boss);
            }
        });
    }

    @Inject(method = {"mUA6spCB5R29Y185106762726", "cwDefence"}, at = @At("HEAD"), cancellable = true, require = 0)
    private static void shizuku$limitDefense(@Coerce Object boss, CallbackInfoReturnable<EventObject> result) {
        if (boss instanceof Entity entity && UnleashedJudgment.coversChaosWither(entity)) {
            result.setReturnValue(null);
        }
    }
}
