package me.sedattr.deluxeauctions.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class XPEconomy implements EconomyManager {
    @Override
    public boolean addBalance(OfflinePlayer player, Double count) {
        Player online = player.getPlayer();
        if (online == null || !online.isOnline())
            return false;

        online.setLevel(online.getLevel()+count.intValue());
        return true;
    }

    @Override
    public boolean removeBalance(OfflinePlayer player, Double count) {
        Player online = player.getPlayer();
        if (online == null || !online.isOnline())
            return false;

        if (getBalance(player) < count)
            return false;

        online.setLevel(online.getLevel()-count.intValue());
        return true;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        Player online = player.getPlayer();
        if (online == null || !online.isOnline())
            return 0.0;

        return online.getLevel();
    }
}
