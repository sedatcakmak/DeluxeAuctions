package me.sedattr.deluxeauctions.inventoryapi.inventory;

import lombok.Getter;
import me.sedattr.deluxeauctions.inventoryapi.HInventory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public class InventoryAPI {
    @Getter private static Plugin instance;

    public static void setup(final Plugin plugin) {
        instance = plugin;

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new InventoryListeners(), plugin);
    }

    public static HInventory getInventory(Player player) {
        return InventoryVariables.getPlayerInventory().get(player.getUniqueId());
    }

    public static boolean hasInventory(Player player) {
        return InventoryVariables.getPlayerInventory().containsKey(player.getUniqueId());
    }

    public static InventoryManager getInventoryManager() {
        return new InventoryManager();
    }

    public static class InventoryManager {
        private String title = "";
        private final InventoryType inventoryType;
        private int size;
        private final boolean closeable;
        private String id;

        public InventoryManager() {
            this.inventoryType = InventoryType.CHEST;
            this.size = 6;
            this.closeable = true;
            this.id = "";
        }

        public InventoryManager setTitle(String title) {
            this.title = title;
            return this;
        }

        public InventoryManager setSize(int size) {
            this.size = size;
            return this;
        }

        public InventoryManager setId(String id) {
            this.id = id;
            return this;
        }

        public HInventory create() {
            return new HInventory(ChatColor.translateAlternateColorCodes('&', this.title), this.inventoryType, this.size, this.id, this.closeable);
        }
    }
}
