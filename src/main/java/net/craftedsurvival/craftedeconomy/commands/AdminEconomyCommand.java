package net.craftedsurvival.craftedeconomy.commands;

import net.craftedsurvival.craftedeconomy.CraftedEconomy;
import net.craftedsurvival.craftedeconomy.database.TransactionEntry;
import net.craftedsurvival.craftedeconomy.database.TransactionType;
import net.craftedsurvival.craftedeconomy.util.TabCompleteUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * {@code /admineconomy} (alias {@code /ae}) — admin balance management and
 * transaction-log viewing. All player resolution and database work runs off the
 * main thread; results are messaged back asynchronously.
 */
public class AdminEconomyCommand implements CommandExecutor, TabCompleter {

    private static final int LOGS_PAGE_SIZE = 10;
    private static final List<String> PAGE_HINTS = List.of("1", "2", "3");
    private static final List<String> SUBCOMMANDS = List.of("add", "subtract", "set", "logs", "reload");

    private final CraftedEconomy plugin;

    public AdminEconomyCommand(CraftedEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getMessages().get("admin-usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> handleAmountChange(sender, args, TransactionType.ADMIN_ADD);
            case "subtract" -> handleAmountChange(sender, args, TransactionType.ADMIN_SUBTRACT);
            case "set" -> handleAmountChange(sender, args, TransactionType.ADMIN_SET);
            case "logs" -> handleLogs(sender, args);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage(plugin.getMessages().get("admin-usage"));
        }
        return true;
    }

    // ── add / subtract / set ──────────────────────────────────────────────────

    private void handleAmountChange(CommandSender sender, String[] args, TransactionType type) {
        if (!sender.hasPermission(permissionFor(type))) {
            sender.sendMessage(plugin.getMessages().get("no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.getMessages().get("admin-usage"));
            return;
        }

        String targetName = args[1];
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessages().get("admin-invalid-amount"));
            return;
        }

        // set allows exactly 0; add/subtract require a strictly positive amount.
        boolean valid = (type == TransactionType.ADMIN_SET) ? amount >= 0 : amount > 0;
        if (!valid) {
            sender.sendMessage(plugin.getMessages().get("admin-invalid-amount"));
            return;
        }

        runAsync(() -> applyAmountChange(sender, targetName, amount, type));
    }

    @SuppressWarnings("deprecation")
    private void applyAmountChange(CommandSender sender, String targetName, double amount, TransactionType type) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(plugin.getMessages().get("admin-player-not-found",
                    Map.of("player", targetName)));
            return;
        }

        UUID uuid = target.getUniqueId();
        String resolvedName = target.getName() != null ? target.getName() : targetName;
        String currency = currencyPlural();

        try {
            // The player has joined before but may predate the plugin — ensure a row exists.
            if (!plugin.getDatabase().playerExists(uuid).join()) {
                plugin.getDatabase().createAccount(uuid, resolvedName).join();
            }

            double before = plugin.getDatabase().getBalance(uuid).join();
            double after;

            switch (type) {
                case ADMIN_ADD -> {
                    after = before + amount;
                    plugin.getDatabase().addBalance(uuid, amount).join();
                    sender.sendMessage(plugin.getMessages().get("admin-add-success", Map.of(
                            "amount", format(amount),
                            "currency", currency,
                            "player", resolvedName,
                            "balance", format(after)
                    )));
                }
                case ADMIN_SUBTRACT -> {
                    boolean clamped = (before - amount) < 0;
                    after = clamped ? 0.0 : before - amount;
                    plugin.getDatabase().setBalance(uuid, after).join();
                    if (clamped) {
                        sender.sendMessage(plugin.getMessages().get("admin-subtract-clamped", Map.of(
                                "currency", currency,
                                "player", resolvedName
                        )));
                    } else {
                        sender.sendMessage(plugin.getMessages().get("admin-subtract-success", Map.of(
                                "amount", format(amount),
                                "currency", currency,
                                "player", resolvedName,
                                "balance", format(after)
                        )));
                    }
                }
                case ADMIN_SET -> {
                    after = amount;
                    plugin.getDatabase().setBalance(uuid, amount).join();
                    sender.sendMessage(plugin.getMessages().get("admin-set-success", Map.of(
                            "amount", format(amount),
                            "currency", currency,
                            "player", resolvedName
                    )));
                }
                default -> {
                    return;
                }
            }

            notifyTarget(uuid, after, currency);
            plugin.getDatabase().logTransaction(uuid, type.name(), amount, before, after,
                    sender.getName(), null);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Admin economy operation (" + type + ") failed for " + targetName, e);
            sender.sendMessage(plugin.getMessages().get("error-generic"));
        }
    }

    private void notifyTarget(UUID uuid, double newBalance, String currency) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            online.sendMessage(plugin.getMessages().get("admin-notify-player", Map.of(
                    "balance", format(newBalance),
                    "currency", currency
            )));
        }
    }

    // ── reload ────────────────────────────────────────────────────────────────

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("craftedeconomy.admin.reload")) {
            sender.sendMessage(plugin.getMessages().get("no-permission"));
            return;
        }
        try {
            plugin.reloadPluginConfig();
            sender.sendMessage(plugin.getMessages().get("admin-reload-success"));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reload config.yml", e);
            sender.sendMessage(plugin.getMessages().get("admin-reload-failed"));
        }
    }

    // ── logs ──────────────────────────────────────────────────────────────────

    private void handleLogs(CommandSender sender, String[] args) {
        if (!sender.hasPermission("craftedeconomy.admin.logs")) {
            sender.sendMessage(plugin.getMessages().get("no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessages().get("admin-usage"));
            return;
        }

        String targetName = args[1];
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getMessages().get("baltop-invalid-page"));
                return;
            }
        }
        if (page < 1) {
            sender.sendMessage(plugin.getMessages().get("baltop-invalid-page"));
            return;
        }

        int finalPage = page;
        runAsync(() -> showLogs(sender, targetName, finalPage));
    }

    @SuppressWarnings("deprecation")
    private void showLogs(CommandSender sender, String targetName, int page) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(plugin.getMessages().get("admin-player-not-found",
                    Map.of("player", targetName)));
            return;
        }

        UUID uuid = target.getUniqueId();
        String resolvedName = target.getName() != null ? target.getName() : targetName;

        try {
            int count = plugin.getDatabase().getTransactionCount(uuid).join();
            if (count == 0) {
                sender.sendMessage(plugin.getMessages().get("logs-empty",
                        Map.of("player", resolvedName)));
                return;
            }

            int totalPages = Math.max(1, (int) Math.ceil((double) count / LOGS_PAGE_SIZE));
            if (page > totalPages) {
                sender.sendMessage(plugin.getMessages().get("baltop-invalid-page"));
                return;
            }

            List<TransactionEntry> entries =
                    plugin.getDatabase().getTransactions(uuid, page, LOGS_PAGE_SIZE).join();
            renderLogs(sender, resolvedName, entries, page, totalPages);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to show logs for " + targetName, e);
            sender.sendMessage(plugin.getMessages().get("error-generic"));
        }
    }

    private void renderLogs(CommandSender sender, String playerName, List<TransactionEntry> entries,
                            int page, int totalPages) {
        String currency = currencyPlural();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        sender.sendMessage(plugin.getMessages().get("logs-header", Map.of("player", playerName)));

        for (TransactionEntry entry : entries) {
            sender.sendMessage(plugin.getMessages().get("logs-entry", Map.of(
                    "id", String.valueOf(entry.id()),
                    "type", entry.type(),
                    "amount", format(entry.amount()),
                    "currency", currency,
                    "balance_after", format(entry.balanceAfter()),
                    "actor", entry.actor() != null ? entry.actor() : "-",
                    "timestamp", dateFormat.format(new Date(entry.timestamp()))
            )));
        }

        sender.sendMessage(plugin.getMessages().get("logs-footer", Map.of(
                "page", String.valueOf(page),
                "total_pages", String.valueOf(totalPages)
        )));

        sendLogsNav(sender, playerName, page, totalPages);
    }

    private void sendLogsNav(CommandSender sender, String playerName, int page, int totalPages) {
        Component nav = Component.empty();

        if (page > 1) {
            int prevPage = page - 1;
            Component prevHover = plugin.getMessages().getRaw("baltop-prev-hover",
                    Map.of("page", String.valueOf(prevPage)));
            Component prev = plugin.getMessages().getRaw("baltop-prev", Map.of())
                    .clickEvent(ClickEvent.runCommand("/admineconomy logs " + playerName + " " + prevPage))
                    .hoverEvent(HoverEvent.showText(prevHover));
            nav = nav.append(prev);
        }

        if (page < totalPages) {
            int nextPage = page + 1;
            Component nextHover = plugin.getMessages().getRaw("baltop-next-hover",
                    Map.of("page", String.valueOf(nextPage)));
            Component next = plugin.getMessages().getRaw("baltop-next", Map.of())
                    .clickEvent(ClickEvent.runCommand("/admineconomy logs " + playerName + " " + nextPage))
                    .hoverEvent(HoverEvent.showText(nextHover));
            if (page > 1) {
                nav = nav.append(Component.text("  "));
            }
            nav = nav.append(next);
        }

        if (page > 1 || page < totalPages) {
            sender.sendMessage(nav);
        }
    }

    // ── tab completion ──────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> allowed = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sender.hasPermission(permissionForSub(sub))) {
                    allowed.add(sub);
                }
            }
            return TabCompleteUtil.filter(allowed, args[0]);
        }

        if (args.length == 2 && SUBCOMMANDS.contains(args[0].toLowerCase())
                && !args[0].equalsIgnoreCase("reload")
                && sender.hasPermission(permissionForSub(args[0].toLowerCase()))) {
            return TabCompleteUtil.filter(TabCompleteUtil.onlinePlayerNames(), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("logs")
                && sender.hasPermission("craftedeconomy.admin.logs")) {
            return TabCompleteUtil.filter(PAGE_HINTS, args[2]);
        }

        return Collections.emptyList();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private void runAsync(Runnable task) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    private String format(double amount) {
        return plugin.getBalanceFormatter().format(amount);
    }

    private String currencyPlural() {
        return plugin.getConfig().getString("currency.name-plural", "Diamonds");
    }

    private static String permissionFor(TransactionType type) {
        return switch (type) {
            case ADMIN_ADD -> "craftedeconomy.admin.add";
            case ADMIN_SUBTRACT -> "craftedeconomy.admin.subtract";
            case ADMIN_SET -> "craftedeconomy.admin.set";
            default -> "craftedeconomy.admin";
        };
    }

    private static String permissionForSub(String sub) {
        return switch (sub) {
            case "add" -> "craftedeconomy.admin.add";
            case "subtract" -> "craftedeconomy.admin.subtract";
            case "set" -> "craftedeconomy.admin.set";
            case "logs" -> "craftedeconomy.admin.logs";
            case "reload" -> "craftedeconomy.admin.reload";
            default -> "craftedeconomy.admin";
        };
    }
}
