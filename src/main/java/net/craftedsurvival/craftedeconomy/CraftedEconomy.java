package net.craftedsurvival.craftedeconomy;

import net.craftedsurvival.craftedeconomy.commands.*;
import net.craftedsurvival.craftedeconomy.database.EconomyDatabase;
import net.craftedsurvival.craftedeconomy.database.MySQLDatabase;
import net.craftedsurvival.craftedeconomy.economy.VaultEconomyProvider;
import net.craftedsurvival.craftedeconomy.listeners.PlayerJoinListener;
import net.craftedsurvival.craftedeconomy.placeholders.EconomyExpansion;
import net.craftedsurvival.craftedeconomy.util.BalanceFormatter;
import net.craftedsurvival.craftedeconomy.util.BaltopCache;
import net.craftedsurvival.craftedeconomy.util.MessageManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class CraftedEconomy extends JavaPlugin {

    private EconomyDatabase database;
    private MessageManager messages;
    private BalanceFormatter balanceFormatter;
    private BaltopCache baltopCache;
    private CraftedEconomyAPI api;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupVault()) {
            getLogger().severe("Vault is not present or has no economy provider hook. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        database = new MySQLDatabase(this);
        try {
            database.init().join();
        } catch (Exception e) {
            getLogger().severe("Failed to connect to the database: " + e.getMessage());
            getLogger().severe("Check your database settings in config.yml and ensure the server is reachable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        messages = new MessageManager(this);
        balanceFormatter = new BalanceFormatter(this);
        baltopCache = new BaltopCache(this);
        api = new CraftedEconomyAPI(this);

        registerVaultProvider();
        registerCommands();
        registerListeners();

        baltopCache.start();

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new EconomyExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        getLogger().info("CraftedEconomy enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (baltopCache != null) baltopCache.stop();

        // Unregister Vault provider
        getServer().getServicesManager().unregisterAll(this);

        if (database != null) database.close();

        getLogger().info("CraftedEconomy disabled.");
    }

    private boolean setupVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        // Vault itself is present; the provider will be registered by us
        return true;
    }

    private void registerVaultProvider() {
        getServer().getServicesManager().register(
                Economy.class,
                new VaultEconomyProvider(this),
                this,
                ServicePriority.Normal
        );
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("balance")).setExecutor(new BalanceCommand(this));
        Objects.requireNonNull(getCommand("pay")).setExecutor(new PayCommand(this));
        Objects.requireNonNull(getCommand("baltop")).setExecutor(new BaltopCommand(this));
        Objects.requireNonNull(getCommand("convert")).setExecutor(new ConvertCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public static CraftedEconomyAPI getAPI() {
        return ((CraftedEconomy) JavaPlugin.getPlugin(CraftedEconomy.class)).api;
    }

    public EconomyDatabase getDatabase() {
        return database;
    }

    public MessageManager getMessages() {
        return messages;
    }

    public BalanceFormatter getBalanceFormatter() {
        return balanceFormatter;
    }

    public BaltopCache getBaltopCache() {
        return baltopCache;
    }
}
