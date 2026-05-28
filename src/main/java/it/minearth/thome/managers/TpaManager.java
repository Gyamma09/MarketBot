package it.minearth.thome.managers;

import it.minearth.thome.THOME;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores pending TPA requests. Each target keeps at most one incoming request
 * (latest wins). Requests auto-expire after the configured timeout.
 */
public class TpaManager {

    private final THOME plugin;

    /** key = target UUID, value = request */
    private final Map<UUID, TpaRequest> pending = new HashMap<>();

    public TpaManager(THOME plugin) {
        this.plugin = plugin;
    }

    public void sendRequest(Player sender, Player target) {
        int timeout = plugin.getConfig().getInt("settings.tpa-timeout-seconds", 60);

        // cancel previous request to same target
        TpaRequest old = pending.remove(target.getUniqueId());
        if (old != null) old.expireTask.cancel();

        BukkitTask expireTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TpaRequest req = pending.remove(target.getUniqueId());
            if (req != null) {
                Player s = Bukkit.getPlayer(req.senderId);
                if (s != null) {
                    s.sendMessage(plugin.getConfigManager().msg("tpa.expired-sender",
                            "%target%", target.getName()));
                }
                if (target.isOnline()) {
                    target.sendMessage(plugin.getConfigManager().msg("tpa.expired-target",
                            "%sender%", sender.getName()));
                }
            }
        }, timeout * 20L);

        pending.put(target.getUniqueId(),
                new TpaRequest(sender.getUniqueId(), target.getUniqueId(), expireTask));
    }

    public boolean hasRequest(UUID target) {
        return pending.containsKey(target);
    }

    public boolean hasOutgoing(UUID sender, UUID target) {
        TpaRequest req = pending.get(target);
        return req != null && req.senderId.equals(sender);
    }

    /**
     * Remove and return the pending request for a target.
     */
    public TpaRequest consume(UUID target) {
        TpaRequest req = pending.remove(target);
        if (req != null) req.expireTask.cancel();
        return req;
    }

    public void clear(UUID uuid) {
        // remove as target
        TpaRequest asTarget = pending.remove(uuid);
        if (asTarget != null) asTarget.expireTask.cancel();
        // remove as sender
        pending.values().removeIf(r -> {
            if (r.senderId.equals(uuid)) {
                r.expireTask.cancel();
                return true;
            }
            return false;
        });
    }

    public static class TpaRequest {
        public final UUID senderId;
        public final UUID targetId;
        public final BukkitTask expireTask;

        TpaRequest(UUID senderId, UUID targetId, BukkitTask expireTask) {
            this.senderId = senderId;
            this.targetId = targetId;
            this.expireTask = expireTask;
        }
    }
}
