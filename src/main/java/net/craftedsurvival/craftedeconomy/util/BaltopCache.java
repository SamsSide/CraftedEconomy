package net.craftedsurvival.craftedeconomy.util;

import net.craftedsurvival.craftedeconomy.CraftedEconomy;
import net.craftedsurvival.craftedeconomy.database.BalanceEntry;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public class BaltopCache {

    private static final int TOP_SIZE = 10;

    private final CraftedEconomy plugin;
    private final AtomicReference<List<BalanceEntry>> cache = new AtomicReference<>(Collections.emptyList());
    private int taskId = -1;

    public BaltopCache(CraftedEconomy plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long refreshSeconds = plugin.getConfig().getLong("baltop.cache-refresh-seconds", 60L);
        long ticks = refreshSeconds * 20L;
        taskId = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::refresh, 0L, ticks)
                .getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public void refresh() {
        plugin.getDatabase().getTopBalances(1, TOP_SIZE)
                .thenAccept(cache::set)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to refresh baltop cache", ex);
                    return null;
                });
    }

    public List<BalanceEntry> getTop() {
        return Collections.unmodifiableList(cache.get());
    }

    public BalanceEntry getEntry(int rank) {
        List<BalanceEntry> list = cache.get();
        int index = rank - 1;
        if (index < 0 || index >= list.size()) return null;
        return list.get(index);
    }
}
