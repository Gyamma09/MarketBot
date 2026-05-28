package it.minearth.thome.gui;

import it.minearth.thome.THOME;
import it.minearth.thome.config.ConfigManager;
import it.minearth.thome.database.Home;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and opens the player's home GUI. DB reads are async; the inventory
 * is created and opened back on the main thread.
 */
public class HomeGUI {

    private final THOME plugin;

    public HomeGUI(THOME plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        var db = plugin.getDatabaseManager();

        db.getHomes(player.getUniqueId()).thenCombine(
                db.getExtraSlots(player.getUniqueId()),
                (homes, extra) -> new Object[]{homes, extra}
        ).thenAccept(arr -> {
            @SuppressWarnings("unchecked")
            List<Home> homes = (List<Home>) arr[0];
            int extra = (int) arr[1];
            int max = plugin.getLimitManager().getTotalLimit(player, extra);

            Bukkit.getScheduler().runTask(plugin, () -> build(player, homes, max));
        });
    }

    private void build(Player player, List<Home> homes, int max) {
        ConfigManager cfg = plugin.getConfigManager();
        var gui = cfg.gui().getConfigurationSection("gui-homes");

        int rows = gui.getInt("rows", 3);
        int size = rows * 9;

        String maxDisplay = (max == Integer.MAX_VALUE) ? "∞" : String.valueOf(max);
        Map<String, String> titlePh = new HashMap<>();
        titlePh.put("current", String.valueOf(homes.size()));
        titlePh.put("max", maxDisplay);
        Component title = cfg.guiText(gui.getString("title", "Home"), titlePh);

        THOMEHolder holder = new THOMEHolder(THOMEHolder.Type.HOMES);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        // filler
        Material filler = cfg.material(gui.getString("filler-item", "GRAY_STAINED_GLASS_PANE"),
                Material.GRAY_STAINED_GLASS_PANE);
        ItemStack fillerItem = simpleItem(filler, Component.empty(), null);
        for (int i = 0; i < size; i++) inv.setItem(i, fillerItem);

        // home items
        Material homeMat = cfg.material(gui.getString("home-item", "CYAN_BED"), Material.CYAN_BED);
        List<String> loreTemplate = gui.getStringList("home-lore");

        int slot = 0;
        int buySlot = gui.getInt("buy-slot-button.slot", 26);
        for (Home home : homes) {
            while (slot == buySlot || slot >= size) slot++;
            if (slot >= size) break;

            Map<String, String> ph = new HashMap<>();
            ph.put("world", home.getWorld());
            ph.put("x", String.valueOf((int) home.getX()));
            ph.put("y", String.valueOf((int) home.getY()));
            ph.put("z", String.valueOf((int) home.getZ()));

            List<Component> lore = new ArrayList<>();
            for (String line : loreTemplate) lore.add(cfg.guiText(line, ph));

            Component name = cfg.guiPlain("<#2ecc71>" + home.getName());
            ItemStack item = simpleItem(homeMat, name, lore);
            inv.setItem(slot, item);
            holder.getHomeSlots().put(slot, home);
            slot++;
        }

        // buy-slot button
        var buyBtn = gui.getConfigurationSection("buy-slot-button");
        if (buyBtn != null) {
            double cost = plugin.getConfig().getDouble("economy.buy-slot-cost", 5000.0);
            Material btnMat = cfg.material(buyBtn.getString("item", "GOLD_INGOT"), Material.GOLD_INGOT);
            Component btnName = cfg.guiPlain(buyBtn.getString("name", "Buy Slot"));
            List<Component> btnLore = new ArrayList<>();
            for (String line : buyBtn.getStringList("lore")) {
                btnLore.add(cfg.guiPlain(line.replace("%cost%", formatMoney(cost))));
            }
            inv.setItem(buySlot, simpleItem(btnMat, btnName, btnLore));
            holder.setBuySlotSlot(buySlot);
        }

        player.openInventory(inv);
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

    private String formatMoney(double amount) {
        if (amount == Math.floor(amount)) return String.valueOf((long) amount);
        return String.format("%.2f", amount);
    }
}
