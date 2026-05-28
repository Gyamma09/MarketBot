package it.minearth.thome.config;

import it.minearth.thome.THOME;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads and exposes config.yml, messages.yml and gui.yml.
 * All user-facing text is parsed through MiniMessage (HEX + tags supported)
 * so it renders correctly across every ViaVersion client.
 */
public class ConfigManager {

    private final THOME plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private FileConfiguration messages;
    private FileConfiguration gui;

    public ConfigManager(THOME plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.messages = loadFile("messages.yml");
        this.gui = loadFile("gui.yml");
    }

    private FileConfiguration loadFile(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /* ------------------------------------------------------------------ */
    /*  Message helpers                                                    */
    /* ------------------------------------------------------------------ */

    public Component prefix() {
        return mm.deserialize(messages.getString("prefix", ""));
    }

    /**
     * Get a prefixed message Component with %placeholder% replacements.
     */
    public Component msg(String path, String... replacements) {
        String raw = messages.getString(path, "<red>Missing message: " + path + "</red>");
        raw = applyRaw(raw, replacements);
        return prefix().append(mm.deserialize(raw));
    }

    /**
     * Get a message Component WITHOUT prefix (for chat lines like TPA buttons).
     */
    public Component msgNoPrefix(String path, String... replacements) {
        String raw = messages.getString(path, "<red>Missing message: " + path + "</red>");
        raw = applyRaw(raw, replacements);
        return mm.deserialize(raw);
    }

    private String applyRaw(String raw, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return raw;
    }

    public Component deserialize(String raw) {
        return mm.deserialize(raw);
    }

    /**
     * Deserialize a GUI string with MiniMessage placeholders supplied as a map.
     */
    public Component guiText(String raw, Map<String, String> placeholders) {
        List<TagResolver> resolvers = new ArrayList<>();
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            resolvers.add(Placeholder.parsed(e.getKey(), e.getValue()));
        }
        // first replace %x% style tokens, then parse minimessage tags
        String replaced = raw;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            replaced = replaced.replace("%" + e.getKey() + "%", e.getValue());
        }
        return mm.deserialize(replaced).decorationIfAbsent(
                net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }

    public Component guiPlain(String raw) {
        return mm.deserialize(raw).decorationIfAbsent(
                net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }

    /* ------------------------------------------------------------------ */
    /*  Material safety helper                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Resolves a material name from config safely. Falls back to a stable
     * default if the name is invalid (protects ViaVersion clients from
     * unknown blocks).
     */
    public Material material(String name, Material fallback) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name.toUpperCase());
        return (m != null && m.isItem()) ? m : fallback;
    }

    /* ------------------------------------------------------------------ */
    /*  Raw accessors                                                      */
    /* ------------------------------------------------------------------ */

    public FileConfiguration messages() { return messages; }
    public FileConfiguration gui() { return gui; }
    public FileConfiguration config() { return plugin.getConfig(); }
}
