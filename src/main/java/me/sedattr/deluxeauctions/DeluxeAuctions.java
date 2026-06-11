package me.sedattr.deluxeauctions;

import lombok.Getter;
import me.sedattr.deluxeauctions.addons.*;
import me.sedattr.deluxeauctions.addons.multiserver.MultiServerManager;
import me.sedattr.deluxeauctions.addons.mute.AdvancedBan;
import me.sedattr.deluxeauctions.addons.mute.BanManager;
import me.sedattr.deluxeauctions.addons.mute.LiteBans;
import me.sedattr.deluxeauctions.addons.mute.MuteManager;
import me.sedattr.auctionsapi.cache.AuctionCache;
import me.sedattr.auctionsapi.cache.CategoryCache;
import me.sedattr.deluxeauctions.commands.AuctionAdminCommand;
import me.sedattr.deluxeauctions.commands.AuctionCommand;
import me.sedattr.deluxeauctions.database.DatabaseManager;
import me.sedattr.deluxeauctions.handlers.BlacklistHandler;
import me.sedattr.deluxeauctions.handlers.DataHandler;
import me.sedattr.deluxeauctions.handlers.MenuHandler;
import me.sedattr.deluxeauctions.inventoryapi.HInventory;
import me.sedattr.deluxeauctions.inventoryapi.inventory.InventoryAPI;
import me.sedattr.deluxeauctions.listeners.PlayerListeners;
import me.sedattr.deluxeauctions.managers.AuctionType;
import me.sedattr.deluxeauctions.managers.CustomItem;
import me.sedattr.deluxeauctions.managers.Economy;
import me.sedattr.deluxeauctions.managers.SortType;
import me.sedattr.deluxeauctions.menus.InputMenu;
import me.sedattr.deluxeauctions.others.*;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("ConstantConditions")
public class DeluxeAuctions extends JavaPlugin {
    @Getter private static DeluxeAuctions instance;

    public YamlConfiguration economyFile;
    public YamlConfiguration menusFile;
    public YamlConfiguration messagesFile;
    public YamlConfiguration categoriesFile;
    public YamlConfiguration itemsFile;
    public FileConfiguration configFile;

    public DataHandler dataHandler;
    public BlacklistHandler blacklistHandler;
    public MenuHandler menuHandler;

    public Map<String, Economy> economies = new HashMap<>();
    public Map<String, ItemStack> normalItems = new HashMap<>();
    public Map<String, CustomItem> customItems = new HashMap<>();

    public DatabaseManager databaseManager;
    public MuteManager muteManager;

    public NumberFormat numberFormat;
    public TimeFormat timeFormat;
    public InputMenu inputMenu;

    public boolean placeholderApi = false;
    public EcoItemsAddon ecoItemsAddon;
    public MultiServerManager multiServerManager;
    public HeadDatabase headDatabase;
    public DiscordWebhook discordWebhook;

    public String returnCategory = "";
    public double fileVersions = 1.4;
    public int version;
    public boolean locked = false;
    public boolean loaded = false;
    public boolean disabled = false;
    public boolean converting = false;
    private Metrics metrics;

    public AuctionType auctionType;
    public SortType sortType;
    public String rarityType;
    public String category;
    public AuctionType createType;
    public double createPrice;
    public int createTime;
    public Economy createEconomy;

    public void registerCommandsListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerListeners(), DeluxeAuctions.getInstance());

        PluginCommand auction = getCommand("auction");
        if (auction != null)
            auction.setExecutor(new AuctionCommand());

        PluginCommand auctionAdmin = getCommand("auctionadmin");
        if (auctionAdmin != null)
            auctionAdmin.setExecutor(new AuctionAdminCommand());
    }

    @Override
    public void onEnable() {
        this.version = Integer.parseInt(Bukkit.getBukkitVersion().substring(2, 4).replace(".", ""));
        instance = this;

        saveDefaultConfig();
        this.metrics = new Metrics(this, 22020);
        if (!getConfig().getBoolean("settings.enable_plugin", true)) {
            Logger.sendConsoleMessage("Plugin is disabled because enable_plugin setting is false in config!", Logger.LogLevel.INFO);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.dataHandler = new DataHandler();
        if (!this.dataHandler.load()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        CategoryCache.loadCategories();

        long time = System.currentTimeMillis();
        if (!this.databaseManager.loadAuctions()) {
            Logger.sendConsoleMessage("There is a problem in database setup! Plugin is disabling...", Logger.LogLevel.ERROR);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            this.databaseManager.loadStat(player.getUniqueId());
            this.databaseManager.loadItem(player.getUniqueId());
        }

        Logger.sendConsoleMessage("Database successfully loaded! Took &f" + (System.currentTimeMillis() - time) + "ms %level_color%to complete!", Logger.LogLevel.INFO);

        loadAddons();
        registerCommandsListeners();
        InventoryAPI.setup(DeluxeAuctions.this);

        if (this.configFile.getBoolean("settings.anti_lag.server.enabled"))
            TaskUtils.runTimerAsync(new ServerTps(), 100L, 1L);

        this.metrics.addCustomChart(new Metrics.SingleLineChart("total_auctions", () -> AuctionCache.getAuctions().size()));

        Logger.sendConsoleMessage("Your server is running on &f1." + this.version + "%level_color%.", Logger.LogLevel.INFO);
        Logger.sendConsoleMessage("Plugin is enabled! Plugin Version: &fv" + getDescription().getVersion(), Logger.LogLevel.INFO);
    }

    @Override
    public void onDisable() {
        this.disabled = true;

        if (this.loaded) {
            DeluxeAuctions.getInstance().databaseManager.saveAuctions();
            DeluxeAuctions.getInstance().databaseManager.shutdown();
        }
        this.metrics.shutdown();

        for (Player player : Bukkit.getOnlinePlayers())
            if (InventoryAPI.hasInventory(player))
                player.closeInventory();

        Logger.sendConsoleMessage("Plugin is disabled! &8(&7sedattr was here...&8)", Logger.LogLevel.WARN);
    }

    public void loadAddons() {
        ConfigurationSection addons = this.configFile.getConfigurationSection("addons");
        if (addons == null)
            return;

        if (Bukkit.getPluginManager().isPluginEnabled("HeadDatabase")) {
            this.headDatabase = new HeadDatabase();
            Bukkit.getPluginManager().registerEvents(this.headDatabase, this);

            Logger.sendConsoleMessage("Enabled &fHeadDatabase %level_color%support!", Logger.LogLevel.INFO);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("EcoItems")) {
            this.ecoItemsAddon = new EcoItemsAddon();
            Logger.sendConsoleMessage("Enabled &fEcoItems %level_color%support!", Logger.LogLevel.INFO);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.placeholderApi = true;
            new Placeholders().register();
            Logger.sendConsoleMessage("Enabled &fPlaceholderAPI %level_color%support!", Logger.LogLevel.INFO);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("BanManager")) {
            this.muteManager = new BanManager();
            Logger.sendConsoleMessage("Enabled &fBanManager %level_color%support!", Logger.LogLevel.INFO);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("AdvancedBan")) {
            this.muteManager = new AdvancedBan();
            Logger.sendConsoleMessage("Enabled &fAdvancedBan %level_color%support!", Logger.LogLevel.INFO);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("LiteBans")) {
            this.muteManager = new LiteBans();
            Logger.sendConsoleMessage("Enabled &fLiteBans %level_color%support!", Logger.LogLevel.INFO);
        }

        if (addons.getBoolean("discord.enabled") && !addons.getString("discord.webhook_url", "").isEmpty()) {
            this.discordWebhook = new DiscordWebhook(addons.getString("discord.webhook_url"));
            Logger.sendConsoleMessage("Enabled &fDiscord Webhook %level_color%support!", Logger.LogLevel.INFO);
        }
    }

    public void reload() {
        Bukkit.getServer().getOnlinePlayers().forEach(player -> {
            HInventory gui = InventoryAPI.getInventory(player);
            if (gui != null)
                player.closeInventory();
        });

        reloadConfig();
        this.dataHandler.load();
        CategoryCache.loadCategories();
    }
}