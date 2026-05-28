package it.minearth.thome.managers;

import it.minearth.thome.THOME;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Centralised teleport logic: cooldown, warmup (cancel on move/damage),
 * combat check, particle + sound feedback. All scheduling stays on the
 * main thread for Bukkit-API safety, while DB work is done elsewhere async.
 */
public class TeleportManager {

    private final THOME plugin;

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, WarmupTask> warmups = new HashMap<>();

    public TeleportManager(THOME plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------------------------------------------ */
    /*  Cooldown                                                           */
    /* ------------------------------------------------------------------ */

    public boolean isOnCooldown(Player player) {
        if (player.hasPermission("thome.admin")) return false;
        Long until = cooldowns.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public long remainingCooldown(Player player) {
        Long until = cooldowns.get(player.getUniqueId());
        if (until == null) return 0;
        long diff = until - System.currentTimeMillis();
        return Math.max(0, (diff + 999) / 1000);
    }

    private void applyCooldown(Player player) {
        int seconds = plugin.getConfig().getInt("settings.teleport-cooldown-seconds", 120);
        if (seconds > 0 && !player.hasPermission("thome.admin")) {
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Warmup                                                             */
    /* ------------------------------------------------------------------ */

    public boolean hasWarmup(UUID uuid) {
        return warmups.containsKey(uuid);
    }

    /**
     * Cancel a running warmup (called on move / damage / quit).
     */
    public void cancelWarmup(Player player, boolean notify) {
        WarmupTask task = warmups.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            if (notify) {
                player.sendMessage(plugin.getConfigManager().msg("general.warmup-cancelled"));
            }
        }
    }

    /**
     * Begin a teleport with warmup. On completion, run {@code onComplete} on the
     * main thread, apply cooldown, play effects.
     */
    public void startTeleport(Player player, Location destination, Runnable onComplete) {
        if (destination == null) {
            player.sendMessage(plugin.getConfigManager().msg("home.not-found"));
            return;
        }

        int warmup = plugin.getConfig().getInt("settings.teleport-warmup-seconds", 5);

        if (warmup <= 0 || player.hasPermission("thome.admin")) {
            doTeleport(player, destination, onComplete);
            return;
        }

        player.sendMessage(plugin.getConfigManager().msg("general.warmup-started",
                "%time%", String.valueOf(warmup)));

        WarmupTask task = new WarmupTask(player, destination, warmup, onComplete);
        warmups.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    private void doTeleport(Player player, Location destination, Runnable onComplete) {
        playEffect(player.getLocation());
        player.teleportAsync(destination).thenAccept(success -> {
            if (success) {
                playEffect(destination);
                applyCooldown(player);
                if (onComplete != null) onComplete.run();
            }
        });
    }

    private void playEffect(Location loc) {
        try {
            String particleName = plugin.getConfig().getString("visuals.particle-effect", "CHERRY_LEAVES");
            Particle particle = Particle.valueOf(particleName.toUpperCase());
            loc.getWorld().spawnParticle(particle, loc.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
        } catch (Throwable ignored) {
        }
        try {
            String soundName = plugin.getConfig().getString("visuals.sound-effect", "ENTITY_ENDERMAN_TELEPORT");
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            loc.getWorld().playSound(loc, sound, 1.0f, 1.0f);
        } catch (Throwable ignored) {
        }
    }

    public void clear(UUID uuid) {
        WarmupTask task = warmups.remove(uuid);
        if (task != null) task.cancel();
        cooldowns.remove(uuid);
    }

    /* ------------------------------------------------------------------ */
    /*  Inner warmup runnable                                              */
    /* ------------------------------------------------------------------ */

    private class WarmupTask extends BukkitRunnable {
        private final Player player;
        private final Location destination;
        private final Runnable onComplete;
        private int remaining;

        WarmupTask(Player player, Location destination, int seconds, Runnable onComplete) {
            this.player = player;
            this.destination = destination;
            this.remaining = seconds;
            this.onComplete = onComplete;
        }

        @Override
        public void run() {
            if (!player.isOnline()) {
                warmups.remove(player.getUniqueId());
                cancel();
                return;
            }
            if (remaining <= 0) {
                warmups.remove(player.getUniqueId());
                cancel();
                doTeleport(player, destination, onComplete);
                return;
            }
            // light ambient particle during warmup
            try {
                player.getWorld().spawnParticle(Particle.PORTAL,
                        player.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.01);
            } catch (Throwable ignored) {
            }
            remaining--;
        }
    }
}
