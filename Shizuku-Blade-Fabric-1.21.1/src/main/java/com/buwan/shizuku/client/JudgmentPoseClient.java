package com.buwan.shizuku.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/**
 * 客户端侧「审判持刀姿势」的计时表：施法者 UUID → 施法起始 gameTime。
 *
 * <p>由 {@code JudgmentPosePacket}（施法第 1 tick 广播）填充，供
 * {@code AvatarRendererPoseMixin} 在 extract 阶段取进度。不依赖库的连招状态。
 * 超过 {@code Ymsa.END_TICK} 自动清理。</p>
 */
public final class JudgmentPoseClient {

    private static final Map<UUID, Long> STARTS = new HashMap<>();
    private static ClientLevel level;

    private JudgmentPoseClient() {
    }

    public static void setStart(UUID caster, long startTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        if (current != level) {
            STARTS.clear();
            level = current;
        }
        STARTS.put(caster, startTick);
    }

    /** 施法经过的 tick；不在审判中返回 -1。 */
    public static float progress(Entity caster) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current == null || current != level) {
            return -1.0F;
        }
        Long start = STARTS.get(caster.getUUID());
        if (start == null) {
            return -1.0F;
        }
        long elapsed = current.getGameTime() - start;
        if (elapsed < 0) {
            return -1.0F;
        }
        if (elapsed >= com.buwan.shizuku.sa.Ymsa.END_TICK) {
            STARTS.remove(caster.getUUID());
            return -1.0F;
        }
        return elapsed;
    }

    public static void clear() {
        STARTS.clear();
        level = null;
    }
}
