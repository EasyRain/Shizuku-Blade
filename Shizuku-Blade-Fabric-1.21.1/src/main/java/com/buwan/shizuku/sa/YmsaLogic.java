package com.buwan.shizuku.sa;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 3.5.0 抹除能力纯逻辑工具（从 Ymsa 中抽出，无 MC API 依赖，可直接移植）。
 */
final class YmsaLogic {
    private YmsaLogic() {
    }

    public static boolean shouldKillPure(boolean isCaster, boolean isOwnPet, boolean isRemoved, boolean isDead) {
        return !isCaster && !isOwnPet && !isRemoved && !isDead;
    }

    public static boolean matchesBossKeywordsPure(String lowerClassName, String lowerDisplayName,
                                                  List<String> classFragments, List<String> nameFragments) {
        if (lowerClassName != null && classFragments != null) {
            for (String fragment : classFragments) {
                if (fragment != null && lowerClassName.contains(fragment)) {
                    return true;
                }
            }
        }

        if (lowerDisplayName != null && nameFragments != null) {
            for (String fragment : nameFragments) {
                if (fragment != null && lowerDisplayName.contains(fragment)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean anyTagPresent(Set<String> presentKeys, List<String> protectionTags) {
        if (presentKeys != null && protectionTags != null) {
            for (String tag : protectionTags) {
                if (presentKeys.contains(tag)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void tickCleanups(Map<UUID, CleanupEntry> map) {
        if (map != null && !map.isEmpty()) {
            map.entrySet().removeIf(entry -> {
                entry.getValue().ticksLeft--;
                return entry.getValue().ticksLeft <= 0;
            });
        }
    }

    public static void tickTypeSuppression(Map<String, Integer> map) {
        if (map != null && !map.isEmpty()) {
            map.replaceAll((key, count) -> count - 1);
            map.entrySet().removeIf(entry -> entry.getValue() <= 0);
        }
    }

    public static void filterByKiller(Map<UUID, CleanupEntry> map, UUID killerUuid) {
        if (map != null && !map.isEmpty() && killerUuid != null) {
            map.entrySet().removeIf(entry -> killerUuid.equals(entry.getValue().killerUuid));
        }
    }

    public static final class CleanupEntry {
        public final UUID killerUuid;
        public int ticksLeft;

        public CleanupEntry(UUID killerUuid, int ticksLeft) {
            this.killerUuid = killerUuid;
            this.ticksLeft = ticksLeft;
        }
    }
}
