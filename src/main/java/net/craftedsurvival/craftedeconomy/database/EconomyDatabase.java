package net.craftedsurvival.craftedeconomy.database;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EconomyDatabase {

    CompletableFuture<Void> init();

    CompletableFuture<Double> getBalance(UUID playerUuid);

    CompletableFuture<Void> setBalance(UUID playerUuid, double amount);

    CompletableFuture<Void> addBalance(UUID playerUuid, double amount);

    CompletableFuture<Void> subtractBalance(UUID playerUuid, double amount);

    /** Atomically subtract only if balance >= amount. Returns true on success, false if insufficient. */
    CompletableFuture<Boolean> trySubtractBalance(UUID playerUuid, double amount);

    CompletableFuture<List<BalanceEntry>> getTopBalances(int page, int pageSize);

    CompletableFuture<Integer> getPlayerCount();

    // ── Transaction logging ───────────────────────────────────────────────────

    /**
     * Insert a transaction log row. This call is best-effort: a failure is logged
     * as SEVERE but never propagates, so a logging error can never roll back the
     * economy operation that triggered it. The returned future always completes
     * normally.
     */
    CompletableFuture<Void> logTransaction(UUID uuid, String type, double amount,
                                           double balanceBefore, double balanceAfter,
                                           String actor, String note);

    /** Fetch a player's transaction history, most recent first. */
    CompletableFuture<List<TransactionEntry>> getTransactions(UUID uuid, int page, int pageSize);

    /** Count the total number of transactions logged for a player. */
    CompletableFuture<Integer> getTransactionCount(UUID uuid);

    CompletableFuture<Boolean> playerExists(UUID playerUuid);

    CompletableFuture<Void> createAccount(UUID playerUuid, String playerName);

    // Synchronous variants for Vault (called on main thread by other plugins)
    double getBalanceSync(UUID playerUuid);
    boolean trySubtractBalanceSync(UUID playerUuid, double amount);
    void addBalanceSync(UUID playerUuid, double amount);
    boolean playerExistsSync(UUID playerUuid);
    void createAccountSync(UUID playerUuid, String playerName);

    void close();
}
