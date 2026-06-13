package net.craftedsurvival.craftedeconomy.commands;

import net.craftedsurvival.craftedeconomy.CraftedEconomy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PayCommand implements CommandExecutor {

    private final CraftedEconomy plugin;

    public PayCommand(CraftedEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player payer)) {
            sender.sendMessage(plugin.getMessages().get("console-only-players"));
            return true;
        }

        if (!payer.hasPermission("craftedeconomy.pay")) {
            payer.sendMessage(plugin.getMessages().get("no-permission"));
            return true;
        }

        if (args.length < 2) {
            payer.sendMessage(plugin.getMessages().get("pay-invalid-amount"));
            return true;
        }

        String targetName = args[0];
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            payer.sendMessage(plugin.getMessages().get("pay-invalid-amount"));
            return true;
        }

        if (amount <= 0) {
            payer.sendMessage(plugin.getMessages().get("pay-invalid-amount"));
            return true;
        }

        double minPay = plugin.getConfig().getDouble("currency.minimum-pay-amount", 0.01);
        String currencyPlural = plugin.getConfig().getString("currency.name-plural", "Diamonds");

        if (amount < minPay) {
            payer.sendMessage(plugin.getMessages().get("pay-minimum-amount", Map.of(
                    "min", plugin.getBalanceFormatter().format(minPay),
                    "currency", currencyPlural
            )));
            return true;
        }

        if (payer.getName().equalsIgnoreCase(targetName)) {
            payer.sendMessage(plugin.getMessages().get("pay-self"));
            return true;
        }

        resolveTarget(payer, targetName, amount);
        return true;
    }

    @SuppressWarnings("deprecation")
    private void resolveTarget(Player payer, String targetName, double amount) {
        Player online = Bukkit.getPlayerExact(targetName);
        OfflinePlayer offline = online != null ? online : Bukkit.getOfflinePlayerIfCached(targetName);

        if (offline == null || (!offline.hasPlayedBefore() && online == null)) {
            payer.sendMessage(plugin.getMessages().get("player-not-found",
                    Map.of("player", targetName)));
            return;
        }

        String resolvedName = offline.getName() != null ? offline.getName() : targetName;
        UUID payerUuid = payer.getUniqueId();
        UUID targetUuid = offline.getUniqueId();
        String currencyPlural = plugin.getConfig().getString("currency.name-plural", "Diamonds");

        plugin.getDatabase().playerExists(targetUuid).thenCompose(exists -> {
            if (!exists) {
                payer.sendMessage(plugin.getMessages().get("player-not-found",
                        Map.of("player", resolvedName)));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
            // Atomic debit: fails if insufficient funds, no race condition
            return plugin.getDatabase().trySubtractBalance(payerUuid, amount)
                    .thenCompose(deducted -> {
                        if (!deducted) {
                            payer.sendMessage(plugin.getMessages().get("pay-not-enough",
                                    Map.of("currency", currencyPlural)));
                            return java.util.concurrent.CompletableFuture.completedFuture(null);
                        }
                        return plugin.getDatabase().addBalance(targetUuid, amount)
                                .thenRun(() -> {
                                    String formatted = plugin.getBalanceFormatter().format(amount);
                                    payer.sendMessage(plugin.getMessages().get("pay-sent", Map.of(
                                            "amount", formatted,
                                            "currency", currencyPlural,
                                            "player", resolvedName
                                    )));
                                    Player recipient = Bukkit.getPlayer(targetUuid);
                                    if (recipient != null) {
                                        recipient.sendMessage(plugin.getMessages().get("pay-received", Map.of(
                                                "amount", formatted,
                                                "currency", currencyPlural,
                                                "player", payer.getName()
                                        )));
                                    }
                                })
                                .exceptionally(ex -> {
                                    // Credit to recipient failed — refund sender
                                    plugin.getLogger().log(Level.SEVERE,
                                            "CRITICAL: pay credit failed after deducting "
                                                    + amount + " from " + payer.getName()
                                                    + ". Refunding sender.", ex);
                                    plugin.getDatabase().addBalance(payerUuid, amount)
                                            .exceptionally(refundEx -> {
                                                plugin.getLogger().log(Level.SEVERE,
                                                        "CRITICAL: refund also failed for " + payer.getName(), refundEx);
                                                return null;
                                            });
                                    payer.sendMessage(plugin.getMessages().get("error-generic"));
                                    return null;
                                });
                    });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Pay transaction failed", ex);
            payer.sendMessage(plugin.getMessages().get("error-generic"));
            return null;
        });
    }
}
