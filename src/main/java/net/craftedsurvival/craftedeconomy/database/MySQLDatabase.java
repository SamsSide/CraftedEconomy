package net.craftedsurvival.craftedeconomy.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.craftedsurvival.craftedeconomy.CraftedEconomy;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class MySQLDatabase implements EconomyDatabase {

    private final CraftedEconomy plugin;
    private final String tablePrefix;
    private final double startingBalance;
    private HikariDataSource dataSource;

    public MySQLDatabase(CraftedEconomy plugin) {
        this.plugin = plugin;
        this.tablePrefix = plugin.getConfig().getString("database.table-prefix", "ce_");
        this.startingBalance = plugin.getConfig().getDouble("currency.starting-balance", 0.0);
    }

    @Override
    public CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(() -> {
            HikariConfig config = new HikariConfig();
            String host = plugin.getConfig().getString("database.host", "localhost");
            int port = plugin.getConfig().getInt("database.port", 3306);
            String dbName = plugin.getConfig().getString("database.name", "craftedeconomy");
            String username = plugin.getConfig().getString("database.username", "root");
            String password = plugin.getConfig().getString("database.password", "password");
            int poolSize = plugin.getConfig().getInt("database.pool-size", 10);

            config.setDriverClassName("net.craftedsurvival.craftedeconomy.libs.mariadb.Driver");
            config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + dbName
                    + "?useSSL=false&characterEncoding=UTF-8");
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(poolSize);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30_000);
            config.setIdleTimeout(600_000);
            config.setMaxLifetime(1_800_000);
            config.setPoolName("CraftedEconomy-Pool");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
            createTable();
            createTransactionTable();
        });
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "balances ("
                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "player_name VARCHAR(16) NOT NULL, "
                + "balance DECIMAL(20,4) NOT NULL DEFAULT 0.0000"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create balance table", e);
            throw new RuntimeException(e);
        }
    }

    private void createTransactionTable() {
        // Indexes are declared inline so the statement is fully idempotent under
        // CREATE TABLE IF NOT EXISTS on both MySQL and MariaDB (CREATE INDEX IF NOT
        // EXISTS is not portable across both engines).
        String sql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "transactions ("
                + "id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, "
                + "uuid VARCHAR(36) NOT NULL, "
                + "type VARCHAR(32) NOT NULL, "
                + "amount DECIMAL(18,4) NOT NULL, "
                + "balance_before DECIMAL(18,4) NOT NULL, "
                + "balance_after DECIMAL(18,4) NOT NULL, "
                + "actor VARCHAR(64) DEFAULT NULL, "
                + "note VARCHAR(255) DEFAULT NULL, "
                + "timestamp BIGINT NOT NULL, "
                + "INDEX idx_transactions_uuid (uuid), "
                + "INDEX idx_transactions_uuid_ts (uuid, timestamp)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create transactions table", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<Double> getBalance(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT balance FROM " + tablePrefix + "balances WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("balance");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get balance for " + playerUuid, e);
                throw new RuntimeException(e);
            }
            return 0.0;
        });
    }

    @Override
    public CompletableFuture<Void> setBalance(UUID playerUuid, double amount) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "balances SET balance = ? WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDouble(1, amount);
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to set balance for " + playerUuid, e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> addBalance(UUID playerUuid, double amount) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "balances SET balance = balance + ? WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDouble(1, amount);
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to add balance for " + playerUuid, e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> subtractBalance(UUID playerUuid, double amount) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "balances SET balance = balance - ? WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDouble(1, amount);
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to subtract balance for " + playerUuid, e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> trySubtractBalance(UUID playerUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> trySubtractBalanceSync(playerUuid, amount));
    }

    @Override
    public boolean trySubtractBalanceSync(UUID playerUuid, double amount) {
        String sql = "UPDATE " + tablePrefix
                + "balances SET balance = balance - ? WHERE uuid = ? AND balance >= ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, playerUuid.toString());
            ps.setDouble(3, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to atomic-subtract for " + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<Integer> getPlayerCount() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + tablePrefix + "balances";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to count players", e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public double getBalanceSync(UUID playerUuid) {
        String sql = "SELECT balance FROM " + tablePrefix + "balances WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("balance") : 0.0;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get balance (sync) for " + playerUuid, e);
            return 0.0;
        }
    }

    @Override
    public void addBalanceSync(UUID playerUuid, double amount) {
        String sql = "UPDATE " + tablePrefix + "balances SET balance = balance + ? WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add balance (sync) for " + playerUuid, e);
        }
    }

    @Override
    public boolean playerExistsSync(UUID playerUuid) {
        String sql = "SELECT 1 FROM " + tablePrefix + "balances WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check existence (sync) for " + playerUuid, e);
            return false;
        }
    }

    @Override
    public void createAccountSync(UUID playerUuid, String playerName) {
        String sql = "INSERT IGNORE INTO " + tablePrefix
                + "balances (uuid, player_name, balance) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerName);
            ps.setDouble(3, startingBalance);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create account (sync) for " + playerName, e);
        }
    }

    @Override
    public CompletableFuture<List<BalanceEntry>> getTopBalances(int page, int pageSize) {
        return CompletableFuture.supplyAsync(() -> {
            int offset = (page - 1) * pageSize;
            String sql = "SELECT uuid, player_name, balance FROM " + tablePrefix
                    + "balances ORDER BY balance DESC LIMIT ? OFFSET ?";
            List<BalanceEntry> entries = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, pageSize);
                ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new BalanceEntry(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("player_name"),
                                rs.getDouble("balance")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get top balances", e);
                throw new RuntimeException(e);
            }
            return entries;
        });
    }

    @Override
    public CompletableFuture<Boolean> playerExists(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM " + tablePrefix + "balances WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to check player existence for " + playerUuid, e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> createAccount(UUID playerUuid, String playerName) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT IGNORE INTO " + tablePrefix
                    + "balances (uuid, player_name, balance) VALUES (?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setDouble(3, startingBalance);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create account for " + playerName, e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> logTransaction(UUID playerUuid, String type, double amount,
                                                  double balanceBefore, double balanceAfter,
                                                  String actor, String note) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + tablePrefix + "transactions "
                    + "(uuid, type, amount, balance_before, balance_after, actor, note, timestamp) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, type);
                ps.setDouble(3, amount);
                ps.setDouble(4, balanceBefore);
                ps.setDouble(5, balanceAfter);
                if (actor != null) ps.setString(6, actor); else ps.setNull(6, Types.VARCHAR);
                if (note != null) ps.setString(7, note); else ps.setNull(7, Types.VARCHAR);
                ps.setLong(8, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                // Best-effort: log but never propagate, so the economy operation is not rolled back.
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to log transaction (" + type + ") for " + playerUuid, e);
            }
        });
    }

    @Override
    public CompletableFuture<List<TransactionEntry>> getTransactions(UUID playerUuid, int page, int pageSize) {
        return CompletableFuture.supplyAsync(() -> {
            int offset = (page - 1) * pageSize;
            String sql = "SELECT id, uuid, type, amount, balance_before, balance_after, actor, note, timestamp "
                    + "FROM " + tablePrefix + "transactions WHERE uuid = ? "
                    + "ORDER BY timestamp DESC LIMIT ? OFFSET ?";
            List<TransactionEntry> entries = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setInt(2, pageSize);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new TransactionEntry(
                                rs.getLong("id"),
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("type"),
                                rs.getDouble("amount"),
                                rs.getDouble("balance_before"),
                                rs.getDouble("balance_after"),
                                rs.getString("actor"),
                                rs.getString("note"),
                                rs.getLong("timestamp")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to fetch transactions for " + playerUuid, e);
                throw new RuntimeException(e);
            }
            return entries;
        });
    }

    @Override
    public CompletableFuture<Integer> getTransactionCount(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + tablePrefix + "transactions WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to count transactions for " + playerUuid, e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
