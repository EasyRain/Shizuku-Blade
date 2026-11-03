package com.buwan.shizuku.mixin;

import com.buwan.shizuku.client.shader.CosmicShaders;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 宇宙刀（星河刀）自带的刀光/飞剑行为 + 26.1 前置的一个空指针兜底。
 *
 * <p><b>兼容策略（本类所有注入都是「可选」的）</b>：</p>
 * <ul>
 *   <li>三处 {@code @Inject} 都标 {@code require = 0}：前置哪天改了方法名/描述符，注入点找不到时
 *       只是本条补丁失效，<b>不会让 mixin 应用失败把游戏崩在启动阶段</b>。</li>
 *   <li>{@code inBlockState} 不走 {@code @Shadow}，改用反射缓存字段：字段被改名/删除时同样只是失效，
 *       而不是硬失败。反射只做一次（类初始化时），开销可忽略。</li>
 *   <li>空值兜底<b>只填空值</b>：前置自己修好（字段不再为 null）或别的附属先填了值，这里都是空操作，
 *       多份同类补丁可以共存、互不覆盖。</li>
 * </ul>
 */
@Mixin(value = EntityAbstractSummonedSword.class, remap = false)
public abstract class EntityAbstractSummonedSwordMixin {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("shizuku/SummonedSwordGuard");

    /**
     * 前置的 {@code inBlockState} 只在 {@code onHitBlock} 里赋值，而 26.1 的 {@code tick}
     * 直接拿它调 {@code BlockState.equals}（1.20.1 / 1.21.1 上游是引用比较 {@code if_acmpeq}，
     * 所以只有 26.1 分支会 NPE —— 已用字节码确认）。
     *
     * <p>后果：任何"还没撞过方块"就被存档/加载的刀光实体，下一次 tick 必定 NPE 崩服
     * （实测禁用本模组后依然复现，属上游问题）。</p>
     */
    private static java.lang.reflect.Field IN_BLOCK_STATE;
    private static boolean inBlockStateResolved;

    /** 惰性解析（不在 &lt;clinit&gt; 里反射自身，避免和 mixin 的静态合并顺序纠缠）。 */
    private static java.lang.reflect.Field inBlockState() {
        if (!inBlockStateResolved) {
            inBlockStateResolved = true;
            IN_BLOCK_STATE = findInBlockState();
            if (IN_BLOCK_STATE == null) {
                LOGGER.warn("[mixin] 前置的 inBlockState 字段不存在，召唤物空指针兜底自动停用（不影响其它功能）");
            } else {
                LOGGER.debug("[mixin] 召唤物空指针兜底已就绪（inBlockState 反射解析成功）");
            }
        }
        return IN_BLOCK_STATE;
    }

    private static java.lang.reflect.Field findInBlockState() {
        for (Class<?> type = EntityAbstractSummonedSword.class;
             type != null && type != Object.class;
             type = type.getSuperclass()) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField("inBlockState");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // 继续往父类找
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void shizuku$fixNullInBlockState(CallbackInfo ci) {
        java.lang.reflect.Field field = inBlockState();
        if (field == null) {
            return;
        }
        Entity self = (Entity) (Object) this;
        if (self.level() == null) {
            return;
        }
        try {
            if (field.get(this) != null) {
                return;
            }
            // 用「空气」当哨兵而不是当前位置的方块：上游 tick 的语义是
            // 「inBlockState 与当前方块<b>不</b>相等 ⇒ 允许 burst」，空气在 inGround 为真时
            // 一定不等于当前方块（实心方块才有碰撞箱），因此行为与 1.20.1 的 null 引用比较一致：
            // 照常 burst，而不是被误判成"已经撞过这个方块"从而只剩 tryDespawn。
            field.set(this, Blocks.AIR.defaultBlockState());
        } catch (Throwable ignored) {
            // 反射不可用（字段被改类型/删除）时静默放弃本补丁
        }
    }

    /**
     * 命中后静默消失。
     *
     * <p>注意必须用 TAIL 而不是 HEAD+cancel：本体的 {@code onHitBlock} 会写 {@code inBlockState}，
     * 而 26.1 的 {@code tick} 里会直接对它调 {@code equals}（没有空判断）；在 HEAD 取消会让该字段
     * 保持 null，下一次 tick 直接 NPE 崩服。</p>
     */
    @Inject(method = "setHitEntity", at = @At("TAIL"), require = 0)
    private void shizuku$disappearOnHit(Entity hitEntity, CallbackInfo ci) {
        if (isMosmicDrive((EntityAbstractSummonedSword) (Object) this)) {
            Entity self = (Entity) (Object) this;
            if (self.isAlive()) {
                self.discard();
            }
        }
    }

    @Inject(method = "burst", at = @At("TAIL"), require = 0)
    private void shizuku$silentRemove(CallbackInfo ci) {
        if (isMosmicDrive((EntityAbstractSummonedSword) (Object) this)) {
            Entity self = (Entity) (Object) this;
            if (self.isAlive()) {
                self.discard();
            }
        }
    }

    private static boolean isMosmicDrive(EntityAbstractSummonedSword drive) {
        if (drive.getShooter() instanceof LivingEntity living) {
            ItemStack held = living.getMainHandItem();
            if (!(held.getItem() instanceof ItemSlashBlade)) {
                return false;
            } else {
                String key = CapabilitySlashBlade.getBladeState(held)
                        .map(s -> s.getTranslationKey())
                        .orElse("");
                return CosmicShaders.COSMIC_BLADE_KEY.equals(key);
            }
        } else {
            return false;
        }
    }
}
