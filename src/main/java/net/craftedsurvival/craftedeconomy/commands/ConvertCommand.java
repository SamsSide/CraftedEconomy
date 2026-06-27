package net.craftedsurvival.craftedeconomy.commands;

import net.craftedsurvival.craftedeconomy.CraftedEconomy;
import net.craftedsurvival.craftedeconomy.database.TransactionType;
import net.craftedsurvival.craftedeconomy.util.TabCompleteUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Console-only conversion command driven by the banker NPC GUI.
 *
 * <p>Syntax: {@code /convert <player> <to|from> <amount>}. The GUI runs this as a
 * console command, passing the name of the player who clicked. Real players are
 * never allowed to invoke it directly, so there is no way to self-convert.</p>
 */
public class ConvertCommand implements CommandExecutor, TabCompleter {

    private static final int STACK_SIZE = 64;
    private static final List<String> DIRECTIONS = List.of("to", "from");
    /** Actor recorded in the transaction log for console-driven conversions. */
    private static final String ACTOR = "console";

    private final CraftedEconomy plugin;

    public ConvertCommand(CraftedEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // Console-only: a real player must never be able to run this themselves.
        if (sender instanceof Player) {
            sender.sendMessage(plugin.getMessages().get("convert-console-only"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getMessages().get("convert-usage"));
            return true;
        }

        Player target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(plugin.getMessages().get("convert-player-offline",
                    Map.of("player", args[0])));
            return true;
        }

        String direction = args[1].toLowerCase();
        if (!direction.equals("to") && !direction.equals("from")) {
            sender.sendMessage(plugin.getMessages().get("convert-invalid-direction"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessages().get("convert-invalid-amount"));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(plugin.getMessages().get("convert-invalid-amount"));
            return true;
        }

        Material currencyItem = parseMaterial();
        double exchangeRate = plugin.getConfig().getDouble("currency.exchange-rate", 1.0);
        String currencyPlural = plugin.getConfig().getString("currency.name-plural", "Diamonds");
        String itemName = currencyItem.name();

        if (direction.equals("to")) {
            convertToVirtual(sender, target, amount, currencyItem, exchangeRate, currencyPlural, itemName);
        } else {
            convertFromVirtual(sender, target, amount, currencyItem, exchangeRate, currencyPlural, itemName);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filter(TabCompleteUtil.onlinePlayerNames(), args[0]);
        }
        if (args.length == 2) {
            return TabCompleteUtil.filter(DIRECTIONS, args[1]);
        }
        return Collections.emptyList();
    }

    private void convertToVirtual(CommandSender sender, Player player, double amount, Material currencyItem,
                                   double exchangeRate, String currencyPlural, String itemName) {
        // amount must be a whole number of items
        if (amount != Math.floor(amount)) {
            sender.sendMessage(plugin.getMessages().get("convert-invalid-amount"));
            return;
        }

        int physicalCount = (int) amount;
        int held = countItems(player, currencyItem);

        if (held < physicalCount) {
            player.sendMessage(plugin.getMessages().get("convert-not-enough-items",
                    Map.of("item", itemName)));
            return;
        }

        double virtualAmount = physicalCount * exchangeRate;

        // Remove items first, then credit
        removeItems(player, currencyItem, physicalCount);
        plugin.getDatabase().addBalance(player.getUniqueId(), virtualAmount)
                .thenRun(() -> {
                    player.sendMessage(plugin.getMessages().get("convert-to-success", Map.of(
                            "physical", String.valueOf(physicalCount),
                            "item", itemName,
                            "virtual", plugin.getBalanceFormatter().format(virtualAmount),
                            "currency", currencyPlural
                    )));
                    plugin.getDatabase().getBalance(player.getUniqueId()).thenAccept(after ->
                            plugin.getDatabase().logTransaction(player.getUniqueId(),
                                    TransactionType.CONVERT_TO.name(), virtualAmount,
                                    after - virtualAmount, after, ACTOR,
                                    physicalCount + " " + itemName + " -> virtual"));
                })
                .exceptionally(ex -> {
                    // Rollback: return items
                    plugin.getLogger().log(Level.SEVERE,
                            "CRITICAL: convert-to credit failed for " + player.getName()
                                    + " after removing " + physicalCount + " " + itemName
                                    + ". Returning items.", ex);
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            player.getInventory().addItem(new ItemStack(currencyItem, physicalCount)));
                    player.sendMessage(plugin.getMessages().get("error-generic"));
                    return null;
                });
    }

    private void convertFromVirtual(CommandSender sender, Player player, double amount, Material currencyItem,
                                     double exchangeRate, String currencyPlural, String itemName) {
        int physicalCount = (int) Math.floor(amount / exchangeRate);

        if (physicalCount <= 0) {
            player.sendMessage(plugin.getMessages().get("convert-amount-too-small"));
            return;
        }

        double virtualCost = physicalCount * exchangeRate;

        // Check inventory space before touching balance
        int slotsNeeded = (int) Math.ceil((double) physicalCount / STACK_SIZE);
        int freeSlotsAvailable = countFreeSlots(player);

        if (freeSlotsAvailable < slotsNeeded) {
            player.sendMessage(plugin.getMessages().get("convert-inventory-full"));
            return;
        }

        plugin.getDatabase().getBalance(player.getUniqueId()).thenAccept(balance -> {
            if (balance < virtualCost) {
                player.sendMessage(plugin.getMessages().get("convert-not-enough-balance",
                        Map.of("currency", currencyPlural)));
                return;
            }

            // Debit first, then give items
            plugin.getDatabase().subtractBalance(player.getUniqueId(), virtualCost)
                    .thenRun(() -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            int remaining = physicalCount;
                            int actualGiven = 0;
                            while (remaining > 0) {
                                int stackAmt = Math.min(remaining, STACK_SIZE);
                                Map<Integer, ItemStack> leftover =
                                        player.getInventory().addItem(new ItemStack(currencyItem, stackAmt));
                                int given = stackAmt - leftover.values().stream()
                                        .mapToInt(ItemStack::getAmount).sum();
                                actualGiven += given;
                                remaining -= stackAmt;
                                if (!leftover.isEmpty()) {
                                    // Inventory filled mid-give — refund cost for items we couldn't deliver
                                    int notGiven = physicalCount - actualGiven;
                                    double refund = notGiven * exchangeRate;
                                    plugin.getLogger().severe(
                                            "convert-from: inventory full mid-give for " + player.getName()
                                                    + ". Refunding " + refund + " (items not delivered: " + notGiven + ").");
                                    plugin.getDatabase().addBalance(player.getUniqueId(), refund)
                                            .exceptionally(ex -> {
                                                plugin.getLogger().log(Level.SEVERE,
                                                        "CRITICAL: refund also failed for " + player.getName(), ex);
                                                return null;
                                            });
                                    break;
                                }
                            }
                            player.sendMessage(plugin.getMessages().get("convert-from-success", Map.of(
                                    "virtual", plugin.getBalanceFormatter().format(actualGiven * exchangeRate),
                                    "currency", currencyPlural,
                                    "physical", String.valueOf(actualGiven),
                                    "item", itemName
                            )));
                            // Log the net amount actually debited (after any mid-give refund).
                            int givenFinal = actualGiven;
                            double netCost = givenFinal * exchangeRate;
                            plugin.getDatabase().getBalance(player.getUniqueId()).thenAccept(after ->
                                    plugin.getDatabase().logTransaction(player.getUniqueId(),
                                            TransactionType.CONVERT_FROM.name(), netCost,
                                            after + netCost, after, ACTOR,
                                            "virtual -> " + givenFinal + " " + itemName));
                        });
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.SEVERE,
                                "CRITICAL: convert-from failed after deducting "
                                        + virtualCost + " from " + player.getName(), ex);
                        player.sendMessage(plugin.getMessages().get("error-generic"));
                        return null;
                    });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to get balance for convert-from", ex);
            player.sendMessage(plugin.getMessages().get("error-generic"));
            return null;
        });
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int toRemove = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - toRemove);
                remaining -= toRemove;
                if (item.getAmount() == 0) {
                    contents[i] = null;
                }
            }
        }
        player.getInventory().setContents(contents);
    }

    private int countFreeSlots(Player player) {
        int free = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                free++;
            }
        }
        return free;
    }

    private Material parseMaterial() {
        String raw = plugin.getConfig().getString("currency.item", "DIAMOND");
        try {
            return Material.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid currency.item in config: " + raw + ". Falling back to DIAMOND.");
            return Material.DIAMOND;
        }
    }
}
