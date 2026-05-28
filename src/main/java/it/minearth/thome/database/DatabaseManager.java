package it.minearth.thome.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import it.minearth.thome.THOME;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles all MySQL persistence through a HikariCP connection pool.
 * Every public read/write is executed asynchronously and never touches the main thread.
 */
public class DatabaseManager {

    private final THOME plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(THOME plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------------------------------------------ */
    /*  Pool lifecycle                                                     */
    /* ------------------------------------------------------------------ */

    public boolean connect() {
        try {
            var cfg = plugin.getConfig();
            String host = cfg.getString("mysql.host", "localhost");
            int port = cfg.getInt("mysql.port", 3306);
            String db = cfg.getString("mysql.database", "minearth");
            String user = cfg.getString("mysql.username", "root");
            String pass = cfg.getString("mysql.password", "");
            int poolSize = cfg.getInt("mysql.pool-size", 10);

            HikariConfig hikari = new HikariConfig();
            hikari.setPoolName("THOME-Pool");
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                    + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true"
                    + "&useUnicode=true&characterEncoding=utf8");
            hikari.setUsername(user);
            hikari.setPassword(pass);
            hikari.setMaximumPoolSize(poolSize);
            hikari.setMinimumIdle(2);
            hikari.setConnectionTimeout(10_000);
            hikari.setMaxLifetime(1_800_000);
            hikari.addDataSourceProperty("cachePrepStmts", "true");
            hikari.addDataSourceProperty("prepStmtCacheSize", "250");
            hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            this.dataSource = new HikariDataSource(hikari);
            createTables();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Impossibile connettersi a MySQL: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private void createTables() throws SQLException {
        String homesTable = "CREATE TABLE IF NOT EXISTS thome_homes ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "name VARCHAR(32) NOT NULL,"
                + "world VARCHAR(64) NOT NULL,"
                + "x DOUBLE NOT NULL,"
                + "y DOUBLE NOT NULL,"
                + "z DOUBLE NOT NULL,"
                + "yaw FLOAT NOT NULL,"
                + "pitch FLOAT NOT NULL,"
                + "PRIMARY KEY (uuid, name)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String slotsTable = "CREATE TABLE IF NOT EXISTS thome_slots ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "extra_slots INT NOT NULL DEFAULT 0,"
                + "tpa_enabled TINYINT(1) NOT NULL DEFAULT 1,"
                + "PRIMARY KEY (uuid)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            s.execute(homesTable);
            s.execute(slotsTable);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Homes                                                              */
    /* ------------------------------------------------------------------ */

    public CompletableFuture<List<Home>> getHomes(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Home> homes = new ArrayList<>();
            String sql = "SELECT name, world, x, y, z, yaw, pitch FROM thome_homes WHERE uuid = ?";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        homes.add(new Home(
                                rs.getString("name"),
                                rs.getString("world"),
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                rs.getFloat("yaw"),
                                rs.getFloat("pitch")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("getHomes error: " + e.getMessage());
            }
            return homes;
        });
    }

    public CompletableFuture<Home> getHome(UUID uuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT name, world, x, y, z, yaw, pitch FROM thome_homes WHERE uuid = ? AND name = ? LIMIT 1";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Home(
                                rs.getString("name"),
                                rs.getString("world"),
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                rs.getFloat("yaw"),
                                rs.getFloat("pitch"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("getHome error: " + e.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<Integer> countHomes(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM thome_homes WHERE uuid = ?";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("countHomes error: " + e.getMessage());
            }
            return 0;
        });
    }

    public CompletableFuture<Boolean> setHome(UUID uuid, Home home) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO thome_homes (uuid, name, world, x, y, z, yaw, pitch) "
                    + "VALUES (?,?,?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), "
                    + "z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, home.getName());
                ps.setString(3, home.getWorld());
                ps.setDouble(4, home.getX());
                ps.setDouble(5, home.getY());
                ps.setDouble(6, home.getZ());
                ps.setFloat(7, home.getYaw());
                ps.setFloat(8, home.getPitch());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("setHome error: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> deleteHome(UUID uuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM thome_homes WHERE uuid = ? AND name = ?";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("deleteHome error: " + e.getMessage());
                return false;
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /*  Extra slots + TPA toggle                                           */
    /* ------------------------------------------------------------------ */

    public CompletableFuture<Integer> getExtraSlots(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT extra_slots FROM thome_slots WHERE uuid = ?";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("getExtraSlots error: " + e.getMessage());
            }
            return 0;
        });
    }

    public CompletableFuture<Boolean> addExtraSlot(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO thome_slots (uuid, extra_slots) VALUES (?, 1) "
                    + "ON DUPLICATE KEY UPDATE extra_slots = extra_slots + 1";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("addExtraSlot error: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> isTpaEnabled(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT tpa_enabled FROM thome_slots WHERE uuid = ?";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBoolean(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("isTpaEnabled error: " + e.getMessage());
            }
            return true; // default enabled
        });
    }

    public CompletableFuture<Boolean> setTpaEnabled(UUID uuid, boolean enabled) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO thome_slots (uuid, tpa_enabled) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE tpa_enabled = VALUES(tpa_enabled)";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setBoolean(2, enabled);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("setTpaEnabled error: " + e.getMessage());
                return false;
            }
        });
    }
}
