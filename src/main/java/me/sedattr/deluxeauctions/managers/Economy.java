package me.sedattr.deluxeauctions.managers;

import lombok.Getter;
import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.deluxeauctions.economy.*;
import me.sedattr.deluxeauctions.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

@Getter
public class Economy {
    private final String key;
    private final String name;
    private final String filterName;
    private final String type;
    private final String text;
    private final String permission;
    private final boolean enabled;
    private EconomyManager manager;
    private final ItemStack item;
    private final int slot;
    private final Boolean roundDurationPricing;

    public Economy(String type) {
        this.type = type;
        this.name = type;
        this.filterName = null;
        this.key = "economy";
        this.text = "%price%";
        this.enabled = true;
        this.permission = "";
        this.item = null;
        this.slot = 0;
        this.roundDurationPricing = null;

        switch (this.type) {
            case "coins_engine":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("CoinsEngine"))
                    break;

                this.manager = new CoinsEngineEconomy(DeluxeAuctions.getInstance().configFile.getString("economy.coinsengine_settings.currency_name", "coins"));
                break;
            case "ed_prison":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("EdPrison"))
                    break;

                this.manager = new EdPrisonEconomy(DeluxeAuctions.getInstance().configFile.getString("economy.edprison_settings.currency_name", "tokens"));
                break;
            case "lands":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("Lands"))
                    break;

                this.manager = new LandsEconomy();
                break;
            case "player_points":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("PlayerPoints"))
                    break;

                this.manager = new PlayerPointsEconomy();
                break;
            case "royaleeconomy_balance":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("RoyaleEconomy"))
                    break;

                this.manager = new RoyaleEconomyBalance();
                break;
            case "royaleeconomy_bank":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("RoyaleEconomy"))
                    break;

                this.manager = new RoyaleEconomyBank();
                break;
            case "skript":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("Skript"))
                    break;

                this.manager = new SkriptEconomy(DeluxeAuctions.getInstance().configFile.getString("economy.skript_settings.currency_name"));
                break;
            case "token_manager":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("TokenManager"))
                    break;

                this.manager = new TokenManagerEconomy();
                break;
            case "ultra_economy":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("UltraEconomy"))
                    break;

                this.manager = new UltraEconomy(DeluxeAuctions.getInstance().configFile.getString("economy.ultraeconomy_settings.currency_name"));
                break;
            case "yaml":
                ConfigurationSection section = DeluxeAuctions.getInstance().configFile.getConfigurationSection("economy.yaml_settings");
                if (section == null)
                    break;

                this.manager = new YamlEconomy(section.getString("folder_name"), section.getString("file_name"), section.getString("node_text"));
                break;
            default:
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("Vault"))
                    break;

                this.manager = new VaultEconomy();
        }
        
        DeluxeAuctions.getInstance().economies.put(this.key, this);
        DeluxeAuctions.getInstance().createEconomy = this;
    }

    public Economy(ConfigurationSection section) {
        this.key = section.getName();
        this.name = section.getString("name", "");
        this.filterName = section.getString("filter_name");
        this.type = section.getString("type", "");
        this.text = section.getString("text", "");
        this.permission = section.getString("permission", "");
        this.enabled = section.getBoolean("enabled", false);
        this.slot = section.getInt("item.slot", 0);
        this.item = Utils.createItemFromSection(section.getConfigurationSection("item"), null);
        this.roundDurationPricing = section.isSet("round_duration_pricing") ? section.getBoolean("round_duration_pricing") : null;

        switch (this.type) {
            case "coins_engine":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("EdPrison"))
                    break;

                this.manager = new CoinsEngineEconomy(section.getString("coinsengine_currency", "coins"));
                break;
            case "ed_prison":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("EdPrison"))
                    break;

                this.manager = new EdPrisonEconomy(section.getString("edprison_currency", "tokens"));
                break;
            case "item":
                this.manager = new ItemEconomy(section.getString("item_currency", "DIAMOND"));
                break;
            case "xp":
                this.manager = new XPEconomy();
                break;
            case "lands":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("Lands"))
                    break;

                this.manager = new LandsEconomy();
                break;
            case "player_points":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("PlayerPoints"))
                    break;

                this.manager = new PlayerPointsEconomy();
                break;
            case "royaleeconomy_balance":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("RoyaleEconomy"))
                    break;

                this.manager = new RoyaleEconomyBalance();
                break;
            case "royaleeconomy_bank":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("RoyaleEconomy"))
                    break;

                this.manager = new RoyaleEconomyBank();
                break;
            case "skript":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("Skript"))
                    break;

                this.manager = new SkriptEconomy(section.getString("skript_currency"));
                break;
            case "token_manager":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("TokenManager"))
                    break;

                this.manager = new TokenManagerEconomy();
                break;
            case "ultra_economy":
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("UltraEconomy"))
                    break;

                this.manager = new UltraEconomy(section.getString("ultraeconomy_currency"));
                break;
            case "yaml":
                this.manager = new YamlEconomy(section.getString("yaml_folder_name"), section.getString("yaml_file_name"), section.getString("yaml_node_text"));
                break;
            default:
                if (!Bukkit.getServer().getPluginManager().isPluginEnabled("Vault"))
                    break;

                this.manager = new VaultEconomy();
        }
        
        if (this.manager == null || !this.enabled || this.type.isEmpty() || this.key.isEmpty() || this.name.isEmpty() || this.text.isEmpty())
            return;

        DeluxeAuctions.getInstance().economies.put(this.key, this);
    }
}