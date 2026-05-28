package it.minearth.thome.gui;

import it.minearth.thome.THOME;
import it.minearth.thome.config.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI shown to the sender before confirming a TPA. The target's head is
 * resolved with Paper's native PlayerProfile API and applied via
 * SkullMeta#setOwnerProfile. The (potentially blocking) profile completion
 * is performed asynchronously to stay safe across all ViaVersion / Geyser
 * clients and to avoid main-thread lag.
 */
public class TPAConfirmGUI {

    private final THOME plugin;

    public TPAConfirmGUI(THOME plugin) {
        this.plugin = plugin;
    }

    public void open(Player sender, Player target) {
        ConfigManager cfg = plugin.getConfigManager();
        var gui = cfg.gui().getConfigurationSection("gui-tpa-confirm");

        int rows = gui.getInt("rows", 3);
        int size = rows * 9;

        Map<String, String> titlePh = new HashMap<>();
        titlePh.put("target", target.getName());
        Component title = cfg.guiText(gui.getString("title", "TPA"), titlePh);

        THOMEHolder holder = new THOMEHolder(THOMEHolder.Type.TPA_CONFIRM);
        holder.setTargetId(target.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        // filler
        Material filler = cfg.material(gui.getString("filler-item", "GRAY_STAINED_GLASS_PANE"),
                Material.GRAY_STAINED_GLASS_PANE);
        ItemStack fillerItem = simpleItem(filler, Component.empty(), null);
        for (int i = 0; i < size; i++) inv.setItem(i, fillerItem);

        // confirm button
        double cost = plugin.getConfig().getDouble("economy.tp-cost", 50.0);
        int confirmSlot = gui.getInt("confirm-button-slot", 15);
        Material confirmMat = cfg.material(gui.getString("confirm-button-item", "GREEN_WOOL"),
                Material.GREEN_WOOL);
        Component confirmName = cfg.guiPlain(gui.getString("confirm-button-name", "CONFIRM"));
        List<Component> confirmLore = new ArrayList<>();
        for (String line : gui.getStringList("confirm-button-lore")) {
            confirmLore.add(cfg.guiPlain(line.replace("%cost%", formatMoney(cost))));
        }
        inv.setItem(confirmSlot, simpleItem(confirmMat, confirmName, confirmLore));
        holder.setConfirmSlot(confirmSlot);

        // placeholder head while the profile resolves
        int headSlot = gui.getInt("head-slot", 11);
        var targetLoc = target.getLocation();
        Map<String, String> headPh = new HashMap<>();
        headPh.put("target", target.getName());
        headPh.put("target_dimension", prettyDimension(target.getWorld().getEnvironment().name()));
        headPh.put("x", String.valueOf((int) targetLoc.getX()));
        headPh.put("y", String.valueOf((int) targetLoc.getY()));
        headPh.put("z", String.valueOf((int) targetLoc.getZ()));

        List<Component> headLore = new ArrayList<>();
        for (String line : gui.getStringList("head-lore")) headLore.add(cfg.guiText(line, headPh));
        Component headName = cfg.guiPlain("<#f1c40f>" + target.getName());

        ItemStack placeholder = simpleItem(Material.PLAYER_HEAD, headName, headLore);
        inv.setItem(headSlot, placeholder);

        // open immediately, then async-resolve and apply the skull
        player_open(sender, inv);
        resolveHeadAsync(sender, target, inv, headSlot, headName, headLore);
    }

    private void player_open(Player sender, Inventory inv) {
        sender.openInventory(inv);
    }

    /**
     * Cross-version safe head resolution: complete the PlayerProfile async,
     * then apply it to the skull back on the main thread.
     */
    private void resolveHeadAsync(Player sender, Player target, Inventory inv,
                                  int headSlot, Component name, List<Component> lore) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfile profile = target.getPlayerProfile();
            try {
                // completes textures without blocking the main thread
                profile.complete(true);
            } catch (Throwable ignored) {
                // offline-mode / Geyser players may not resolve; placeholder stays
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!sender.isOnline()) return;
                // make sure the sender still has this exact GUI open
                if (!(sender.getOpenInventory().getTopInventory().getHolder() instanceof THOMEHolder h)
                        || h.getType() != THOMEHolder.Type.TPA_CONFIRM) {
                    return;
                }
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                ItemMeta meta = head.getItemMeta();
                if (meta instanceof SkullMeta skull) {
                    try {
                        skull.setOwnerProfile(profile);
                    } catch (Throwable ignored) {
                    }
                    skull.displayName(name);
                    skull.lore(lore);
                    head.setItemMeta(skull);
                }
                inv.setItem(headSlot, head);
            });
        });
    }

    private ItemStack simpleItem(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null) meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String prettyDimension(String env) {
        return switch (env) {
            case "NETHER" -> "Nether";
            case "THE_END" -> "End";
            default -> "Overworld";
        };
    }

    private String formatMoney(double amount) {
        if (amount == Math.floor(amount)) return String.valueOf((long) amount);
        return String.format("%.2f", amount);
    }
}
