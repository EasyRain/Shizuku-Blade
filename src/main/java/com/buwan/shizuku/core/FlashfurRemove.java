package com.buwan.shizuku.core;

import java.lang.reflect.Field;
import java.util.ArrayList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import sun.misc.Unsafe;

/**
 * 针对 flashfur/omnimobs 的一个特殊清理：用 Unsafe 直接把它的「元能闪电兽列表」
 * size 置 0，绕过它自己的不可删除逻辑。
 *
 * 已知问题（上游行为）：这里是 hardcode 的第三方类名，对方改名/改结构时只会静默失败。
 */
public class FlashfurRemove {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("shizuku/flashfurremove");
    public static void forceCleanList() {
        try {
            Class<?> clazz = Class.forName("flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurLevel");
            Field listField = clazz.getDeclaredField("metapotentFlashfurList");
            listField.setAccessible(true);
            ArrayList<?> list = (ArrayList<?>)listField.get(null);

            try {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                Unsafe unsafe = (Unsafe)f.get(null);
                long sizeOffset = unsafe.objectFieldOffset(ArrayList.class.getDeclaredField("size"));
                unsafe.putInt(list, sizeOffset, 0);
            } catch (Exception var7) {
                list.clear();
            }
        } catch (ClassNotFoundException var8) {
        } catch (Exception var9) {
            LOGGER.debug("unexpected error", var9);
        }
    }

    public static boolean attackWithMaxDamage(Entity entity, Player player) {
        if (entity instanceof LivingEntity le) {
            try {
                le.setHealth(0.1F);
                DamageSource damageSource = player.level().damageSources().playerAttack(player);
                le.hurt(damageSource, Float.MAX_VALUE);
                return true;
            } catch (Exception var4) {
                LOGGER.debug("unexpected error", var4);
                return false;
            }
        } else {
            return false;
        }
    }
}
