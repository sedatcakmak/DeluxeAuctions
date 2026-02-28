package me.sedattr.deluxeauctions.economy;

import me.sedattr.deluxeauctions.DeluxeAuctions;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class YamlEconomy implements EconomyManager {
    private final String folder;
    private final String file;
    private final String node;
    private final Object lock = new Object();

    public YamlEconomy(String folder, String file, String node) {
        this.folder = folder;
        this.file = file;
        this.node = node;
    }

    public String replace(OfflinePlayer player, String text) {
        String message = DeluxeAuctions.getInstance().configFile.getString(text);
        if (message == null || message.isEmpty())
            return "";

        if (player == null || player.getName() == null)
            return message;

        return message
                .replace("%player_uuid%", player.getUniqueId().toString())
                .replace("%player_name%", player.getName());
    }

    public boolean addBalance(OfflinePlayer player, Double count) {
        synchronized (lock) {
            File file = new File(replace(player, this.folder), replace(player, this.file));
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            Double oldCount = config.getDouble(replace(player, this.node));

            config.set(replace(player, this.node), oldCount + count);
            try {
                config.save(file);
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }

    @Override
    public boolean removeBalance(OfflinePlayer player, Double count) {
        synchronized (lock) {
            File file = new File(replace(player, this.folder),
                    replace(player, this.file));
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            Double oldCount = config.getDouble(replace(player, this.node));

            if (oldCount < count) return false;

            config.set(replace(player, this.node), oldCount - count);
            try {
                config.save(file);
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        synchronized (lock) {
            File file = new File(replace(player, this.folder),
                    replace(player, this.file));
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            return config.getDouble(replace(player, this.node));
        }
    }
}
