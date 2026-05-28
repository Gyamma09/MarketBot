package it.minearth.thome.commands;

import it.minearth.thome.THOME;
import it.minearth.thome.database.Home;
import it.minearth.thome.managers.TpaManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles every command registered in plugin.yml.
 */
public class CommandHandler implements CommandExecutor, TabCompleter {

    private final THOME plugin;

    public CommandHandler(THOME plugin) {
        this.plugin = plugin;
    }

    public void register() {
        String[] cmds = {"home", "sethome", "delhome", "homes",
                "tpa", "tpaccept", "tpdeny", "tpatoggle", "thome"};
        for (String c : cmds) {
            var pc = plugin.getCommand(c);
            if (pc != null) {
                pc.setExecutor(this);
                pc.setTabCompleter(this);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();

        if (name.equals("thome")) {
            handleAdmin(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().msg("general.player-only"));
            return true;
        }
        if (!player.hasPermission("thome.use")) {
            player.sendMessage(plugin.getConfigManager().msg("general.no-permission"));
            return true;
        }

        switch (name) {
            case "home" -> handleHome(player, args);
            case "homes" -> plugin.getHomeGUI().open(player);
            case "sethome" -> handleSetHome(player, args);
            case "delhome" -> handleDelHome(player, args);
            case "tpa" -> handleTpa(player, args);
            case "tpaccept" -> handleTpAccept(player);
            case "tpdeny" -> handleTpDeny(player);
            case "tpatoggle" -> handleTpaToggle(player);
            default -> { }
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /*  Admin                                                              */
    /* ------------------------------------------------------------------ */

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("thome.admin")) {
            sender.sendMessage(plugin.getConfigManager().msg("general.no-permission"));
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.getConfigManager().load();
            sender.sendMessage(plugin.getConfigManager().msg("general.reloaded"));
        } else {
            sender.sendMessage(plugin.getConfigManager().deserialize(
                    "<#f1c40f>/thome reload"));
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Home                                                               */
    /* ------------------------------------------------------------------ */

    private void handleHome(Player player, String[] args) {
        if (args.length == 0) {
            plugin.getHomeGUI().open(player);
            return;
        }
        String homeName = args[0].toLowerCase();
        plugin.getDatabaseManager().getHome(player.getUniqueId(), homeName).thenAccept(home -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (home == null) {
                    player.sendMessage(plugin.getConfigManager().msg("home.not-found"));
                    return;
                }
                attemptTeleport(player, home.toLocation(),
                        () -> player.sendMessage(plugin.getConfigManager().msg("home.teleporting")));
            });
        });
    }

    private void handleSetHome(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(plugin.getConfigManager().msg("general.usage-sethome"));
            return;
        }
        String homeName = args[0].toLowerCase();
        var db = plugin.getDatabaseManager();

        db.getHome(player.getUniqueId(), homeName).thenAccept(existing -> {
            if (existing != null) {
                // overwrite allowed -> just update, but still counts as existing
                db.setHome(player.getUniqueId(), Home.fromLocation(homeName, player.getLocation()))
                        .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () ->
                                player.sendMessage(plugin.getConfigManager().msg(
                                        "home.set-success", "%name%", homeName))));
                return;
            }
            // new home: check limit
            db.countHomes(player.getUniqueId()).thenCombine(
                    db.getExtraSlots(player.getUniqueId()),
                    (count, extra) -> new int[]{count, extra}
            ).thenAccept(data -> {
                int count = data[0];
                int extra = data[1];
                int max = plugin.getLimitManager().getTotalLimit(player, extra);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (count >= max) {
                        String maxDisp = (max == Integer.MAX_VALUE) ? "∞" : String.valueOf(max);
                        player.sendMessage(plugin.getConfigManager().msg(
                                "home.max-reached", "%max%", maxDisp));
                        return;
                    }
                    db.setHome(player.getUniqueId(), Home.fromLocation(homeName, player.getLocation()))
                            .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () ->
                                    player.sendMessage(plugin.getConfigManager().msg(
                                            "home.set-success", "%name%", homeName))));
                });
            });
        });
    }

    private void handleDelHome(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(plugin.getConfigManager().msg("general.usage-delhome"));
            return;
        }
        String homeName = args[0].toLowerCase();
        plugin.getDatabaseManager().deleteHome(player.getUniqueId(), homeName).thenAccept(deleted ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (deleted) {
                        player.sendMessage(plugin.getConfigManager().msg(
                                "home.delete-success", "%name%", homeName));
                    } else {
                        player.sendMessage(plugin.getConfigManager().msg("home.not-found"));
                    }
                }));
    }

    /* ------------------------------------------------------------------ */
    /*  TPA                                                                */
    /* ------------------------------------------------------------------ */

    private void handleTpa(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(plugin.getConfigManager().msg("general.usage-tpa"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(plugin.getConfigManager().msg("tpa.player-offline"));
            return;
        }
        if (target.equals(player)) {
            player.sendMessage(plugin.getConfigManager().msg("tpa.self-request"));
            return;
        }
        // check target accepts TPA, then open the confirm GUI
        plugin.getDatabaseManager().isTpaEnabled(target.getUniqueId()).thenAccept(enabled ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!enabled) {
                        player.sendMessage(plugin.getConfigManager().msg("tpa.player-offline"));
                        return;
                    }
                    plugin.getTpaConfirmGUI().open(player, target);
                }));
    }

    private void handleTpAccept(Player player) {
        TpaManager.TpaRequest req = plugin.getTpaManager().consume(player.getUniqueId());
        if (req == null) {
            player.sendMessage(plugin.getConfigManager().msg("tpa.no-pending"));
            return;
        }
        Player requester = Bukkit.getPlayer(req.senderId);
        if (requester == null || !requester.isOnline()) {
            player.sendMessage(plugin.getConfigManager().msg("tpa.player-offline"));
            return;
        }

        // combat check on the one teleporting (the requester)
        if (plugin.getCombatManager().isInCombat(requester)) {
            requester.sendMessage(plugin.getConfigManager().msg("general.in-combat"));
            player.sendMessage(plugin.getConfigManager().msg("tpa.player-offline"));
            return;
        }

        double cost = plugin.getConfig().getDouble("economy.tp-cost", 50.0);
        if (!plugin.getEconomyManager().has(requester, cost)) {
            requester.sendMessage(plugin.getConfigManager().msg(
                    "general.no-money", "%cost%", formatMoney(cost)));
            return;
        }

        player.sendMessage(plugin.getConfigManager().msg(
                "tpa.accepted-target", "%sender%", requester.getName()));
        requester.sendMessage(plugin.getConfigManager().msg(
                "tpa.accepted-sender", "%target%", player.getName()));

        attemptTeleport(requester, player.getLocation(), () -> {
            plugin.getEconomyManager().withdraw(requester, cost);
        });
    }

    private void handleTpDeny(Player player) {
        TpaManager.TpaRequest req = plugin.getTpaManager().consume(player.getUniqueId());
        if (req == null) {
            player.sendMessage(plugin.getConfigManager().msg("tpa.no-pending"));
            return;
        }
        Player requester = Bukkit.getPlayer(req.senderId);
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(plugin.getConfigManager().msg(
                    "tpa.denied-sender", "%target%", player.getName()));
        }
        player.sendMessage(plugin.getConfigManager().msg(
                "tpa.denied-target", "%sender%",
                requester != null ? requester.getName() : "?"));
    }

    private void handleTpaToggle(Player player) {
        plugin.getDatabaseManager().isTpaEnabled(player.getUniqueId()).thenAccept(enabled -> {
            boolean newState = !enabled;
            plugin.getDatabaseManager().setTpaEnabled(player.getUniqueId(), newState).thenAccept(ok ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (newState) {
                            player.sendMessage(plugin.getConfigManager().msg("tpa.toggle-on"));
                        } else {
                            player.sendMessage(plugin.getConfigManager().msg("tpa.toggle-off"));
                        }
                    }));
        });
    }

    /* ------------------------------------------------------------------ */
    /*  Shared teleport pre-checks                                         */
    /* ------------------------------------------------------------------ */

    private void attemptTeleport(Player player, org.bukkit.Location dest, Runnable onComplete) {
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
        plugin.getTeleportManager().startTeleport(player, dest, onComplete);
    }

    private String formatMoney(double amount) {
        if (amount == Math.floor(amount)) return String.valueOf((long) amount);
        return String.format("%.2f", amount);
    }

    /* ------------------------------------------------------------------ */
    /*  Tab completion                                                     */
    /* ------------------------------------------------------------------ */

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();

        if (name.equals("tpa") && args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (name.equals("thome") && args.length == 1) {
            List<String> out = new ArrayList<>();
            if ("reload".startsWith(args[0].toLowerCase())) out.add("reload");
            return out;
        }
        return Collections.emptyList();
    }
}
