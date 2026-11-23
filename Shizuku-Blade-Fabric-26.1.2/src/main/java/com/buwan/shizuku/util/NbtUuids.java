package com.buwan.shizuku.util;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * 26.1 的 {@link CompoundTag} 不再提供 {@code putUUID/getUUID/hasUUID}，
 * UUID 统一按原版惯例存成「4 个 int 的 IntArray」。
 *
 * <p>这里把旧写法包一层，保持调用方语义不变：
 * {@code tag.putUUID(k, id)} → {@link #put(CompoundTag, String, UUID)}，
 * {@code tag.getUUID(k)} → {@link #get(CompoundTag, String)}（缺失时返回 null），
 * {@code tag.hasUUID(k)} → {@link #has(CompoundTag, String)}。</p>
 */
public final class NbtUuids {
    private NbtUuids() {}

    public static boolean has(CompoundTag tag, String key) {
        return tag != null && tag.contains(key);
    }

    public static void put(CompoundTag tag, String key, UUID id) {
        long msb = id.getMostSignificantBits();
        long lsb = id.getLeastSignificantBits();
        tag.putIntArray(key, new int[]{
                (int) (msb >> 32), (int) msb, (int) (lsb >> 32), (int) lsb
        });
    }

    /** 读 UUID；键不存在或格式不对时返回 {@code null}（与旧的 {@code getUUID} 一致）。 */
    public static UUID get(CompoundTag tag, String key) {
        if (tag == null) {
            return null;
        }
        Optional<int[]> raw = tag.getIntArray(key);
        if (raw.isEmpty() || raw.get().length != 4) {
            return null;
        }
        int[] a = raw.get();
        long msb = ((long) a[0] << 32) | (a[1] & 0xFFFFFFFFL);
        long lsb = ((long) a[2] << 32) | (a[3] & 0xFFFFFFFFL);
        return new UUID(msb, lsb);
    }
}
