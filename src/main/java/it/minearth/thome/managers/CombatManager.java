package it.minearth.thome.managers;

import it.minearth.thome.THOME;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Detects whether a player is in combat by softly hooking the most common
 * combat plugins (CombatLogX, DeluxeCombat). Uses reflection so the plugin
 * compiles and runs even when none of them are installed.
 */
public class CombatManager {

    private final THOME plugin;

    private boolean combatLogX = false;
    private Object clxApi;
    private Method clxIsInCombat;

    private boolean deluxeCombat = false;
    private Method dcGetInstance;
    private Method dcGetCombatManager;
    private Method dcIsInCombat;

    public CombatManager(THOME plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        // CombatLogX
        if (plugin.getServer().getPluginManager().getPlugin("CombatLogX") != null) {
            try {
                Object clxPlugin = plugin.getServer().getPluginManager().getPlugin("CombatLogX");
                Method getApi = clxPlugin.getClass().getMethod("getCombatLogX");
                this.clxApi = getApi.invoke(clxPlugin);
                Method getCombatManager = clxApi.getClass().getMethod("getCombatManager");
                Object cm = getCombatManager.invoke(clxApi);
                this.clxIsInCombat = cm.getClass().getMethod("isInCombat", Player.class);
                this.clxApi = cm; // store the combat manager directly
                this.combatLogX = true;
                plugin.getLogger().info("Hook CombatLogX attivo.");
            } catch (Throwable t) {
                plugin.getLogger().warning("CombatLogX presente ma hook fallito: " + t.getMessage());
            }
        }

        // DeluxeCombat
        if (plugin.getServer().getPluginManager().getPlugin("DeluxeCombat") != null) {
            try {
                // DeluxeCombat exposes its own API; attempt generic reflection
                Class<?> deluxe = Class.forName("nl.marido.deluxecombat.DeluxeCombat");
                this.dcGetInstance = deluxe.getMethod("getInstance");
                Object instance = dcGetInstance.invoke(null);
                this.dcGetCombatManager = instance.getClass().getMethod("getCombatManager");
                Object cm = dcGetCombatManager.invoke(instance);
                this.dcIsInCombat = cm.getClass().getMethod("isTagged", Player.class);
                this.deluxeCombat = true;
                plugin.getLogger().info("Hook DeluxeCombat attivo.");
            } catch (Throwable t) {
                plugin.getLogger().warning("DeluxeCombat presente ma hook fallito: " + t.getMessage());
            }
        }
    }

    public boolean isInCombat(Player player) {
        if (!plugin.getConfig().getBoolean("settings.block-in-combat", true)) {
            return false;
        }
        if (combatLogX && clxApi != null && clxIsInCombat != null) {
            try {
                Object res = clxIsInCombat.invoke(clxApi, player);
                if (res instanceof Boolean b && b) return true;
            } catch (Throwable ignored) {
            }
        }
        if (deluxeCombat && dcIsInCombat != null) {
            try {
                Object instance = dcGetInstance.invoke(null);
                Object cm = dcGetCombatManager.invoke(instance);
                Object res = dcIsInCombat.invoke(cm, player);
                if (res instanceof Boolean b && b) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
}
