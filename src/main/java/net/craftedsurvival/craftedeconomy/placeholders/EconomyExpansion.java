package net.craftedsurvival.craftedeconomy.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.craftedsurvival.craftedeconomy.CraftedEconomy;
import net.craftedsurvival.craftedeconomy.database.BalanceEntry;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

public class EconomyExpansion extends PlaceholderExpansion {

    private static final long TX_COUNT_CACHE_MILLIS = 60_000L;

    private final CraftedEconomy plugin;
    private final ConcurrentHashMap<UUID, CachedCount> txCountCache = new ConcurrentHashMap<>();

    public EconomyExpansion(CraftedEconomy plugin) {
        this.plugin = plugin;
    }

    private record CachedCount(int value, long expiresAt) {}

    @Override
    public @NotNull String getIdentifier() {
        return "craftedeconomy";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SamsSide";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equals("balance")) {
            return getBalance(player, true);
        }
        if (params.equals("balance_raw")) {
            return getBalance(player, false);
        }
        if (params.equals("rank")) {
            return getRank(player);
        }
        if (params.equals("transactions_count")) {
            return getTransactionCount(player);
        }
        if (params.startsWith("baltop_")) {
            return getBaltopPlaceholder(params);
        }
        return null;
    }

    private String getTransactionCount(OfflinePlayer player) {
        if (player == null) return "0";
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        CachedCount cached = txCountCache.get(uuid);
        if (cached != null && cached.expiresAt() > now) {
            return String.valueOf(cached.value());
        }

        try {
            int count = plugin.getDatabase().getTransactionCount(uuid).get();
            txCountCache.put(uuid, new CachedCount(count, now + TX_COUNT_CACHE_MILLIS));
            return String.valueOf(count);
        } catch (InterruptedException | ExecutionException e) {
            plugin.getLogger().log(Level.WARNING,
                    "PAPI transaction count lookup failed for " + player.getName(), e);
            return cached != null ? String.valueOf(cached.value()) : "0";
        }
    }

    private String getBalance(OfflinePlayer player, boolean formatted) {
        if (player == null) return "0";
        try {
            double balance = plugin.getDatabase().getBalance(player.getUniqueId()).get();
            return formatted ? plugin.getBalanceFormatter().format(balance) : String.valueOf(balance);
        } catch (InterruptedException | ExecutionException e) {
            plugin.getLogger().log(Level.WARNING, "PAPI balance lookup failed for " + player.getName(), e);
            return "0";
        }
    }

    private String getRank(OfflinePlayer player) {
        if (player == null) return "N/A";
        List<BalanceEntry> top = plugin.getBaltopCache().getTop();
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).uuid().equals(player.getUniqueId())) {
                return String.valueOf(i + 1);
            }
        }
        return "N/A";
    }

    private String getBaltopPlaceholder(String params) {
        // Format: baltop_<rank>_name or baltop_<rank>_balance
        String[] parts = params.split("_");
        if (parts.length != 3) return null;

        int rank;
        try {
            rank = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }

        if (rank < 1 || rank > 10) return null;

        BalanceEntry entry = plugin.getBaltopCache().getEntry(rank);
        if (entry == null) return "N/A";

        return switch (parts[2]) {
            case "name" -> entry.playerName();
            case "balance" -> plugin.getBalanceFormatter().format(entry.balance());
            default -> null;
        };
    }
}
