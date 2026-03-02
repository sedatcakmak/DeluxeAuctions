package me.sedattr.deluxeauctions.economy;

import com.edwardbelt.edprison.EdPrison;
import com.edwardbelt.edprison.api.models.EconomyModel;
import org.bukkit.OfflinePlayer;

public class EdPrisonEconomy implements EconomyManager {
    private final String economy;
    private final EconomyModel economyAPI;

    public EdPrisonEconomy(String currency) {
        this.economyAPI = EdPrison.getInstance().getApi().getEconomyApi();
        this.economy = currency;
    }

    @Override
    public boolean addBalance(OfflinePlayer player, Double count) {
        this.economyAPI.addEco(player.getUniqueId(), this.economy, count);
        return true;
    }

    @Override
    public boolean removeBalance(OfflinePlayer player, Double count) {
        if (getBalance(player) < count)
            return false;

        this.economyAPI.removeEco(player.getUniqueId(), this.economy, count);
        return true;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return this.economyAPI.getEco(player.getUniqueId(), this.economy);
    }
}
