package net.craftedsurvival.craftedeconomy.commands;

import net.craftedsurvival.craftedeconomy.CraftedEconomy;
import net.craftedsurvival.craftedeconomy.database.BalanceEntry;
import net.craftedsurvival.craftedeconomy.util.TabCompleteUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BaltopCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PAGE_HINTS = List.of("1", "2", "3");

    private final CraftedEconomy plugin;

    public BaltopCommand(CraftedEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("craftedeconomy.baltop")) {
            sender.sendMessage(plugin.getMessages().get("no-permission"));
            return true;
        }

        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getMessages().get("baltop-invalid-page"));
                return true;
            }
        }

        if (page < 1) {
            sender.sendMessage(plugin.getMessages().get("baltop-invalid-page"));
            return true;
        }

        int pageSize = plugin.getConfig().getInt("baltop.page-size", 10);
        int finalPage = page;

        // Run count and page fetch in parallel, then render
        java.util.concurrent.CompletableFuture<Integer> countFuture = plugin.getDatabase().getPlayerCount();
        java.util.concurrent.CompletableFuture<List<BalanceEntry>> entriesFuture =
                plugin.getDatabase().getTopBalances(page, pageSize);

        countFuture.thenCombine(entriesFuture, (count, entries) -> {
            int totalPages = Math.max(1, (int) Math.ceil((double) count / pageSize));
            if (finalPage > totalPages || (entries.isEmpty() && finalPage > 1)) {
                sender.sendMessage(plugin.getMessages().get("baltop-invalid-page"));
                return null;
            }
            renderPage(sender, entries, finalPage, totalPages, pageSize);
            return null;
        }).exceptionally(ex -> {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Baltop query failed", ex);
            sender.sendMessage(plugin.getMessages().get("error-generic"));
            return null;
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("craftedeconomy.baltop")) {
            return TabCompleteUtil.filter(PAGE_HINTS, args[0]);
        }
        return Collections.emptyList();
    }

    private void renderPage(CommandSender sender, List<BalanceEntry> entries,
                            int page, int totalPages, int pageSize) {
        String currencyPlural = plugin.getConfig().getString("currency.name-plural", "Diamonds");
        int baseRank = (page - 1) * pageSize + 1;

        sender.sendMessage(plugin.getMessages().get("baltop-header", Map.of(
                "page", String.valueOf(page),
                "total_pages", String.valueOf(totalPages)
        )));

        for (int i = 0; i < entries.size(); i++) {
            BalanceEntry entry = entries.get(i);
            sender.sendMessage(plugin.getMessages().get("baltop-entry", Map.of(
                    "rank", String.valueOf(baseRank + i),
                    "player", entry.playerName(),
                    "balance", plugin.getBalanceFormatter().format(entry.balance()),
                    "currency", currencyPlural
            )));
        }

        sender.sendMessage(plugin.getMessages().get("baltop-footer"));

        // Navigation buttons
        Component nav = Component.empty();

        if (page > 1) {
            int prevPage = page - 1;
            Component prevHover = plugin.getMessages().getRaw("baltop-prev-hover",
                    Map.of("page", String.valueOf(prevPage)));
            Component prev = plugin.getMessages().getRaw("baltop-prev", Map.of())
                    .clickEvent(ClickEvent.runCommand("/baltop " + prevPage))
                    .hoverEvent(HoverEvent.showText(prevHover));
            nav = nav.append(prev);
        }

        if (page < totalPages) {
            int nextPage = page + 1;
            Component nextHover = plugin.getMessages().getRaw("baltop-next-hover",
                    Map.of("page", String.valueOf(nextPage)));
            Component next = plugin.getMessages().getRaw("baltop-next", Map.of())
                    .clickEvent(ClickEvent.runCommand("/baltop " + nextPage))
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
}
