package it.minearth.thome;

import it.minearth.thome.commands.CommandHandler;
import it.minearth.thome.config.ConfigManager;
import it.minearth.thome.database.DatabaseManager;
import it.minearth.thome.gui.HomeGUI;
import it.minearth.thome.gui.TPAConfirmGUI;
import it.minearth.thome.listeners.EventListener;
import it.minearth.thome.managers.CombatManager;
import it.minearth.thome.managers.EconomyManager;
import it.minearth.thome.managers.LimitManager;
import it.minearth.thome.managers.TeleportManager;
import it.minearth.thome.managers.TpaManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * THOME Minearth - Home (MySQL) + TPA system with fully configurable GUIs.
 */
public final class THOME extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private LimitManager limitManager;
    private EconomyManager economyManager;
    private CombatManager combatManager;
    private TeleportManager teleportManager;
    private TpaManager tpaManager;
    private HomeGUI homeGUI;
    private TPAConfirmGUI tpaConfirmGUI;

    @Override
    public void onEnable() {
        // Config
        this.configManager = new ConfigManager(this);
        configManager.load();

        // Database
        this.databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("Connessione MySQL fallita. Disabilito il plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Managers / hooks
        this.limitManager = new LimitManager(this);
        this.economyManager = new EconomyManager(this);
        economyManager.setup();
        this.combatManager = new CombatManager(this);
        combatManager.setup();
        this.teleportManager = new TeleportManager(this);
        this.tpaManager = new TpaManager(this);

        // GUIs
        this.homeGUI = new HomeGUI(this);
        this.tpaConfirmGUI = new TPAConfirmGUI(this);

        // Commands
        new CommandHandler(this).register();

        // Listeners
        getServer().getPluginManager().registerEvents(new EventListener(this), this);

        getLogger().info("THOME Minearth abilitato correttamente.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
        getLogger().info("THOME Minearth disabilitato.");
    }

    /* getters */
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public LimitManager getLimitManager() { return limitManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public CombatManager getCombatManager() { return combatManager; }
    public TeleportManager getTeleportManager() { return teleportManager; }
    public TpaManager getTpaManager() { return tpaManager; }
    public HomeGUI getHomeGUI() { return homeGUI; }
    public TPAConfirmGUI getTpaConfirmGUI() { return tpaConfirmGUI; }
}
