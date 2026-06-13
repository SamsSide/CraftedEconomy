package net.craftedsurvival.craftedeconomy.listeners;

import net.craftedsurvival.craftedeconomy.CraftedEconomy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.logging.Level;

public class PlayerJoinListener implements Listener {

    private final CraftedEconomy plugin;

    public PlayerJoinListener(CraftedEconomy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getDatabase().playerExists(player.getUniqueId())
                .thenAccept(exists -> {
                    if (!exists) {
                        plugin.getDatabase()
                                .createAccount(player.getUniqueId(), player.getName())
                                .exceptionally(ex -> {
                                    plugin.getLogger().log(Level.SEVERE,
                                            "Failed to create account for " + player.getName(), ex);
                                    return null;
                                });
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE,
                            "Failed to check existence for " + player.getName(), ex);
                    return null;
                });
    }
}
