package net.craftedsurvival.craftedeconomy.commands;

import net.craftedsurvival.craftedeconomy.CraftedEconomy;
import net.craftedsurvival.craftedeconomy.util.TabCompleteUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class BalanceCommand implements CommandExecutor, TabCompleter {

    private final CraftedEconomy plugin;

    public BalanceCommand(CraftedEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("craftedeconomy.balance")) {
            sender.sendMessage(plugin.getMessages().get("no-permission"));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessages().get("console-only-players"));
                return true;
            }
            showBalance(sender, player.getUniqueId(), player.getName());
        } else {
            if (!sender.hasPermission("craftedeconomy.balance.others")) {
                sender.sendMessage(plugin.getMessages().get("no-permission"));
                return true;
            }
            String targetName = args[0];
            lookupAndShow(sender, targetName);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("craftedeconomy.balance.others")) {
            return TabCompleteUtil.filter(TabCompleteUtil.onlinePlayerNames(), args[0]);
        }
        return Collections.emptyList();
    }

    private void showBalance(CommandSender sender, UUID uuid, String name) {
        String currencyPlural = plugin.getConfig().getString("currency.name-plural", "Diamonds");
        plugin.getDatabase().getBalance(uuid)
                .thenAccept(balance -> {
                    String formatted = plugin.getBalanceFormatter().format(balance);
                    boolean isSelf = sender instanceof Player p && p.getUniqueId().equals(uuid);
                    String key = isSelf ? "balance-self" : "balance-other";
                    sender.sendMessage(plugin.getMessages().get(key, Map.of(
                            "balance", formatted,
                            "currency", currencyPlural,
                            "player", name
                    )));
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to get balance", ex);
                    sender.sendMessage(plugin.getMessages().get("error-generic"));
                    return null;
                });
    }

    @SuppressWarnings("deprecation")
    private void lookupAndShow(CommandSender sender, String targetName) {
        // Try online first
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            showBalance(sender, online.getUniqueId(), online.getName());
            return;
        }

        // Fall back to offline player lookup by name
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(targetName);
        if (offline != null && offline.hasPlayedBefore()) {
            UUID uuid = offline.getUniqueId();
            plugin.getDatabase().playerExists(uuid).thenAccept(exists -> {
                if (!exists) {
                    sender.sendMessage(plugin.getMessages().get("player-not-found",
                            Map.of("player", targetName)));
                    return;
                }
                showBalance(sender, uuid, offline.getName() != null ? offline.getName() : targetName);
            }).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to lookup player", ex);
                sender.sendMessage(plugin.getMessages().get("error-generic"));
                return null;
            });
        } else {
            sender.sendMessage(plugin.getMessages().get("player-not-found",
                    Map.of("player", targetName)));
        }
    }
}
