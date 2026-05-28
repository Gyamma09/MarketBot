package it.minearth.thome.listeners;

import it.minearth.thome.THOME;
import it.minearth.thome.database.Home;
import it.minearth.thome.gui.THOMEHolder;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Central event listener:
 *  - protects and handles THOME GUIs (anti dupe/steal)
 *  - cancels teleport warmups on movement/damage
 *  - registers beds as homes (auto-bed-home)
 *  - cleans state on quit
 */
public class EventListener implements Listener {

    private final THOME plugin;

    public EventListener(THOME plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------------------------------------------ */
    /*  GUI protection + handling                                          */
    /* ------------------------------------------------------------------ */

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof THOMEHolder thome)) return;

        // Always block any item manipulation inside our GUIs.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        // ignore clicks in the player's own inventory portion
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof THOMEHolder)) {
            return;
        }

        int slot = event.getRawSlot();

        if (thome.getType() == THOMEHolder.Type.HOMES) {
            handleHomesClick(player, thome, slot);
        } else if (thome.getType() == THOMEHolder.Type.TPA_CONFIRM) {
            handleTpaConfirmClick(player, thome, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof THOMEHolder) {
            event.setCancelled(true);
        }
    }

    private void handleHomesClick(Player player, THOMEHolder holder, int slot) {
        // teleport to a home
        Home home = holder.getHomeSlots().get(slot);
        if (home != null) {
            player.closeInventory();
            if (plugin.getCombatManager().isInCombat(player)) {
                player.sendMessage(plugin.getConfigManager().msg("general.in-combat"));
                return;
            }
            if (plugin.getTeleportManager().isOnCooldown(player)) {
                long secs = plugin.getTeleportManager().remainingCooldown(player);
                player.sendMessage(plugin.getConfigManager().msg(
                        "general.cooldown-active", "%time%", String.valueOf(secs)));
                return;
            }
            plugin.getTeleportManager().startTeleport(player, home.toLocation(),
                    () -> player.sendMessage(plugin.getConfigManager().msg("home.teleporting")));
            return;
        }

        // buy slot
        if (slot == holder.getBuySlotSlot()) {
            handleBuySlot(player);
        }
    }

    private void handleBuySlot(Player player) {
        double cost = plugin.getConfig().getDouble("economy.buy-slot-cost", 5000.0);
        if (!plugin.getEconomyManager().has(player, cost)) {
            player.sendMessage(plugin.getConfigManager().msg(
                    "general.no-money", "%cost%", formatMoney(cost)));
            return;
        }
        if (!plugin.getEconomyManager().withdraw(player, cost)) {
            player.sendMessage(plugin.getConfigManager().msg(
                    "general.no-money", "%cost%", formatMoney(cost)));
            return;
        }
        plugin.getDatabaseManager().addExtraSlot(player.getUniqueId()).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(plugin.getConfigManager().msg(
                            "home.slot-purchased", "%cost%", formatMoney(cost)));
                    // refresh GUI
                    plugin.getHomeGUI().open(player);
                }));
    }

    private void handleTpaConfirmClick(Player player, THOMEHolder holder, int slot) {
        if (slot != holder.getConfirmSlot()) return;

        Player target = holder.getTargetId() != null
                ? Bukkit.getPlayer(holder.getTargetId()) : null;
        player.closeInventory();

        if (target == null || !target.isOnline()) {
            player.sendMessage(plugin.getConfigManager().msg("tpa.player-offline"));
            return;
        }
        if (plugin.getTpaManager().hasOutgoing(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().msg("tpa.already-sent"));
            return;
        }

        plugin.getTpaManager().sendRequest(player, target);
        player.sendMessage(plugin.getConfigManager().msg(
                "tpa.sent-sender", "%target%", target.getName()));
        target.sendMessage(plugin.getConfigManager().msgNoPrefix(
                "tpa.received-target", "%sender%", player.getName()));
    }

    /* ------------------------------------------------------------------ */
    /*  Warmup cancellation                                                */
    /* ------------------------------------------------------------------ */

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getTeleportManager().hasWarmup(event.getPlayer().getUniqueId())) return;
        // only cancel if the player actually moved a block (not just head rotation)
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            plugin.getTeleportManager().cancelWarmup(event.getPlayer(), true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getTeleportManager().hasWarmup(player.getUniqueId())) {
            plugin.getTeleportManager().cancelWarmup(player, true);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Auto bed home                                                      */
    /* ------------------------------------------------------------------ */

    @EventHandler
    public void onBedInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("settings.auto-bed-home", true)) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (!event.getPlayer().isSneaking()) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        String type = block.getType().name();
        if (!type.endsWith("_BED")) return;

        Player player = event.getPlayer();
        Home bedHome = Home.fromLocation("bed", block.getLocation().add(0.5, 0, 0.5));
        plugin.getDatabaseManager().setHome(player.getUniqueId(), bedHome).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.getConfigManager().msg("home.bed-home-set"))));
    }

    /* ------------------------------------------------------------------ */
    /*  Cleanup                                                            */
    /* ------------------------------------------------------------------ */

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        plugin.getTeleportManager().clear(uuid);
        plugin.getTpaManager().clear(uuid);
    }

    private String formatMoney(double amount) {
        if (amount == Math.floor(amount)) return String.valueOf((long) amount);
        return String.format("%.2f", amount);
    }
}
