package me.sedattr.deluxeauctions.economy;

import me.qKing12.RoyaleEconomy.API.Balance;
import me.qKing12.RoyaleEconomy.RoyaleEconomy;
import org.bukkit.OfflinePlayer;

public class RoyaleEconomyBalance implements EconomyManager {
    private final Balance api;

    public RoyaleEconomyBalance() {
        this.api = RoyaleEconomy.getApiHandler().getBalance();
    }

    @Override
    public boolean addBalance(OfflinePlayer player, Double count) {
        this.api.addBalance(player.getUniqueId().toString(), count);

        return true;
    }

    @Override
    public boolean removeBalance(OfflinePlayer player, Double count) {
        if (getBalance(player) < count)
            return false;

        this.api.removeBalance(player.getUniqueId().toString(), count);
        return true;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return this.api.getBalance(player.getUniqueId().toString());
    }
}
