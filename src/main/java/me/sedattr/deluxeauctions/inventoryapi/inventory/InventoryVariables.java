package me.sedattr.deluxeauctions.inventoryapi.inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;
import me.sedattr.deluxeauctions.inventoryapi.HInventory;
import org.bukkit.entity.Player;

public class InventoryVariables {
    @Getter private static final Map<UUID, HInventory> playerInventory = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

    public static Long getCooldown(Player player) {
        return cooldown.getOrDefault(player.getUniqueId(), 0L);
    }

    public static void addCooldown(Player player, Long time) {
        cooldown.put(player.getUniqueId(), time);
    }

    public static void addPlayerInventory(Player player, HInventory inventory) {
        playerInventory.put(player.getUniqueId(), inventory);
    }

    public static void removePlayerInventory(Player player) {
        playerInventory.remove(player.getUniqueId());
    }

    public static void removeCooldown(Player player) {
        cooldown.remove(player.getUniqueId());
    }
}