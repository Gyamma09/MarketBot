package it.minearth.thome.managers;

import it.minearth.thome.THOME;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * Resolves the dynamic home limit for a player.
 *
 * The limit is computed by scanning every effective permission of the form
 * "thome.limit.<n>" (provided by LuckPerms or any permission plugin) and
 * taking the highest <n>. If none is found, the config "default" value is used.
 * The bought extra slots (MySQL) are then summed on top of that base limit.
 */
public class LimitManager {

    private static final String PREFIX = "thome.limit.";

    private final THOME plugin;

    public LimitManager(THOME plugin) {
        this.plugin = plugin;
    }

    /**
     * Base limit derived from permissions / config (without bought slots).
     */
    public int getPermissionLimit(Player player) {
        int highest = -1;

        // thome.limit.* wildcard => effectively unlimited (use config default as floor anyway)
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission().toLowerCase();
            if (perm.startsWith(PREFIX)) {
                String suffix = perm.substring(PREFIX.length());
                if (suffix.equals("*")) {
                    return Integer.MAX_VALUE;
                }
                try {
                    int value = Integer.parseInt(suffix);
                    if (value > highest) highest = value;
                } catch (NumberFormatException ignored) {
                    // not a numeric limit node
                }
            }
        }

        if (highest < 0) {
            return plugin.getConfigManager().config().getInt("home-limits.default", 5);
        }
        return highest;
    }

    /**
     * Total max homes = permission/config base + bought extra slots.
     */
    public int getTotalLimit(Player player, int extraSlots) {
        int base = getPermissionLimit(player);
        if (base == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return base + extraSlots;
    }
}
