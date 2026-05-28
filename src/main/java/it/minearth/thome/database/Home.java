package it.minearth.thome.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Immutable representation of a player home stored in MySQL.
 */
public class Home {

    private final String name;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public Home(String name, String world, double x, double y, double z, float yaw, float pitch) {
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static Home fromLocation(String name, Location loc) {
        return new Home(name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch());
    }

    public String getName() { return name; }
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    /**
     * @return the Bukkit Location, or null if the world is not currently loaded.
     */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }
}
