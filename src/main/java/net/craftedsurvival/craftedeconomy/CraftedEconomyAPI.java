package net.craftedsurvival.craftedeconomy;

import net.craftedsurvival.craftedeconomy.database.BalanceEntry;
import org.bukkit.Material;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CraftedEconomyAPI {

    private final CraftedEconomy plugin;

    CraftedEconomyAPI(CraftedEconomy plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Double> getBalance(UUID uuid) {
        return plugin.getDatabase().getBalance(uuid);
    }

    public CompletableFuture<Boolean> setBalance(UUID uuid, double amount) {
        return plugin.getDatabase().playerExists(uuid).thenCompose(exists -> {
            if (!exists) return CompletableFuture.completedFuture(false);
            return plugin.getDatabase().setBalance(uuid, amount).thenApply(v -> true);
        });
    }

    public CompletableFuture<Boolean> deposit(UUID uuid, double amount) {
        return plugin.getDatabase().playerExists(uuid).thenCompose(exists -> {
            if (!exists) return CompletableFuture.completedFuture(false);
            return plugin.getDatabase().addBalance(uuid, amount).thenApply(v -> true);
        });
    }

    public CompletableFuture<Boolean> withdraw(UUID uuid, double amount) {
        return plugin.getDatabase().getBalance(uuid).thenCompose(balance -> {
            if (balance < amount) return CompletableFuture.completedFuture(false);
            return plugin.getDatabase().subtractBalance(uuid, amount).thenApply(v -> true);
        });
    }

    public CompletableFuture<Boolean> hasBalance(UUID uuid, double amount) {
        return plugin.getDatabase().getBalance(uuid).thenApply(balance -> balance >= amount);
    }

    public CompletableFuture<List<BalanceEntry>> getTopBalances(int page) {
        int pageSize = plugin.getConfig().getInt("baltop.page-size", 10);
        return plugin.getDatabase().getTopBalances(page, pageSize);
    }

    public String getCurrencyName(boolean plural) {
        if (plural) {
            return plugin.getConfig().getString("currency.name-plural", "Diamonds");
        }
        return plugin.getConfig().getString("currency.name-singular", "Diamond");
    }

    public Material getCurrencyItem() {
        String raw = plugin.getConfig().getString("currency.item", "DIAMOND");
        try {
            return Material.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.DIAMOND;
        }
    }
}
