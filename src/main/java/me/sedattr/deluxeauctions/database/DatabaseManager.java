package me.sedattr.deluxeauctions.database;

import me.sedattr.deluxeauctions.managers.Auction;
import me.sedattr.deluxeauctions.managers.PlayerStats;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public interface DatabaseManager {
    void deleteAuction(String uuid);

    void loadAuction(UUID uuid);
    boolean loadAuctions();

    void loadStat(UUID uuid);
    void loadItem(UUID uuid);

    void saveAuction(Auction auction);
    void saveAuctions();

    void saveItem(UUID uuid, ItemStack item);
    void saveStats(PlayerStats stats);

    PlayerStats loadStatsSync(UUID uuid);
    void deleteStats(UUID uuid);

    void shutdown();
}