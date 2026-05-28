package it.minearth.thome.managers;

import it.minearth.thome.THOME;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Soft Vault hook. If Vault (or an economy provider) is missing, every cost
 * check passes for free so the plugin keeps working.
 */
public class EconomyManager {

    private final THOME plugin;
    private Economy economy;
    private boolean enabled = false;

    public EconomyManager(THOME plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault non trovato: i costi economici sono disabilitati.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().info("Nessun provider economico Vault: costi disabilitati.");
            return;
        }
        this.economy = rsp.getProvider();
        this.enabled = true;
        plugin.getLogger().info("Hook economia Vault attivo: " + economy.getName());
    }

    public boolean isEnabled() {
        return enabled && economy != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (!isEnabled() || amount <= 0) return true;
        return economy.has(player, amount);
    }

    /**
     * Withdraw money. Returns true if charged (or if economy is disabled / free).
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!isEnabled() || amount <= 0) return true;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }
}
