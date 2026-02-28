package me.sedattr.auctionsapi.cache;

import lombok.Getter;
import me.sedattr.deluxeauctions.managers.PlayerPreferences;
import me.sedattr.deluxeauctions.managers.PlayerStats;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerCache {
    @Getter private static final Map<UUID, PlayerStats> stats = new ConcurrentHashMap<>();
    @Getter private static final Map<UUID, PlayerPreferences> players = new ConcurrentHashMap<>();

    public static PlayerStats getStats(UUID player) {
        PlayerStats playerStats = stats.get(player);
        if (playerStats == null) {
            playerStats = new PlayerStats(player);

            stats.put(player, playerStats);
            return playerStats;
        }
        return playerStats;
    }

    @Getter private static final Map<UUID, ItemStack> items = new ConcurrentHashMap<>();
    public static ItemStack getItem(UUID player) {
        return items.getOrDefault(player, null);
    }

    public static void setItem(UUID player, ItemStack item) {
        if (item == null)
            items.remove(player);
        else
            items.put(player, item);
    }

    public static PlayerPreferences getPreferences(UUID player) {
        PlayerPreferences preferences = players.get(player);
        if (preferences == null) {
            preferences = new PlayerPreferences(player);

            players.put(player, preferences);
            return preferences;
        }
        return preferences;
    }

    public static void removeStats(UUID player) {
        stats.remove(player);
    }

    public static void removeItem(UUID player) {
        items.remove(player);
    }

    public static void removePreferences(UUID player) {
        players.remove(player);
    }
}