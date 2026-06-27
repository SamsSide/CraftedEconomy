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
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Level;

public final class CraftedEconomy extends JavaPlugin {

    private EconomyDatabase database;
    private MessageManager messages;
    private volatile BalanceFormatter balanceFormatter;
    private BaltopCache baltopCache;
    private CraftedEconomyAPI api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mergeConfigDefaults();

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

    /**
     * Non-destructive merge of the bundled default {@code config.yml} into the user's
     * on-disk file. Any key present in the jar default but missing on disk is added
     * (at any nesting depth); existing user values are never overwritten and keys the
     * user added that are absent from the default are never removed. When additions are
     * made the file is saved and the in-memory config reloaded so the new keys are
     * available immediately in this same startup.
     */
    private void mergeConfigDefaults() {
        InputStream defaultStream = getResource("config.yml");
        if (defaultStream == null) {
            return;
        }
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));

        File configFile = new File(getDataFolder(), "config.yml");
        // Load the on-disk file directly (no defaults attached) so isSet() reflects only
        // what the user actually has, not values inherited from the bundled default.
        YamlConfiguration current = YamlConfiguration.loadConfiguration(configFile);

        int added = 0;
        for (String key : defaults.getKeys(true)) {
            // Only copy leaf values; parent sections are created implicitly when a child
            // leaf is set, which keeps sibling keys untouched.
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            if (!current.isSet(key)) {
                current.set(key, defaults.get(key));
                added++;
            }
        }

        if (added == 0) {
            return;
        }

        try {
            current.save(configFile);
            reloadConfig();
            getLogger().info("config.yml updated: added " + added + " missing key(s) from this version's defaults.");
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to save merged config.yml; new default keys were not persisted.", e);
        }
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
        register("balance", new BalanceCommand(this));
        register("pay", new PayCommand(this));
        register("baltop", new BaltopCommand(this));
        register("convert", new ConvertCommand(this));
        register("admineconomy", new AdminEconomyCommand(this));
    }

    /** Register an executor and, if it also implements {@link TabCompleter}, its completer. */
    private void register(String name, CommandExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name),
                "Command '" + name + "' is missing from plugin.yml");
        command.setExecutor(executor);
        if (executor instanceof TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
    }

    /**
     * Hot-reloads {@code config.yml} from disk and refreshes all config-derived state
     * that is safe to update at runtime. The database connection pool is not rebuilt —
     * changes to {@code database.*} settings in config require a full server restart.
     */
    public void reloadPluginConfig() {
        reloadConfig();
        balanceFormatter = new BalanceFormatter(this);
        getLogger().info("config.yml reloaded. Database connection settings require a full restart to take effect.");
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
