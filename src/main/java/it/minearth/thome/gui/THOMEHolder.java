package it.minearth.thome.gui;

import it.minearth.thome.database.Home;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Custom InventoryHolder used to safely identify THOME GUIs in the click
 * listener (avoids fragile title-string matching across versions/clients).
 */
public class THOMEHolder implements InventoryHolder {

    public enum Type { HOMES, TPA_CONFIRM }

    private final Type type;
    private Inventory inventory;

    /** slot -> home (for HOMES gui) */
    private final Map<Integer, Home> homeSlots = new HashMap<>();

    /** buy-slot button slot (for HOMES gui) */
    private int buySlotSlot = -1;

    /** confirm button slot + target (for TPA_CONFIRM gui) */
    private int confirmSlot = -1;
    private UUID targetId;

    public THOMEHolder(Type type) {
        this.type = type;
    }

    public Type getType() { return type; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }

    public Map<Integer, Home> getHomeSlots() { return homeSlots; }

    public int getBuySlotSlot() { return buySlotSlot; }
    public void setBuySlotSlot(int slot) { this.buySlotSlot = slot; }

    public int getConfirmSlot() { return confirmSlot; }
    public void setConfirmSlot(int slot) { this.confirmSlot = slot; }

    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }
}
