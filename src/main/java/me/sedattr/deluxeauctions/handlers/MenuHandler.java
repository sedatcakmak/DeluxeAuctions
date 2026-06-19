package me.sedattr.deluxeauctions.handlers;

import me.sedattr.deluxeauctions.inventoryapi.inventory.InventoryAPI;
import me.sedattr.deluxeauctions.inventoryapi.HInventory;
import me.sedattr.deluxeauctions.inventoryapi.item.ClickableItem;
import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.deluxeauctions.managers.Category;
import me.sedattr.deluxeauctions.others.Logger;
import me.sedattr.deluxeauctions.others.PlaceholderUtil;
import me.sedattr.deluxeauctions.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MenuHandler {
    private final boolean packCheckEnabled;
    private final boolean packCheckSupported;
    private final Map<String, String> packTitles = new HashMap<>();

    public MenuHandler() {
        FileConfiguration config = DeluxeAuctions.getInstance().configFile;
        this.packCheckEnabled = config.getBoolean("pack-check.enabled", false);

        // Player#hasResourcePack is not available on all server versions
        boolean supported = false;
        try {
            Player.class.getMethod("hasResourcePack");
            supported = true;
        } catch (NoSuchMethodException ignored) {}
        this.packCheckSupported = supported;

        if (this.packCheckEnabled && !this.packCheckSupported)
            Logger.sendConsoleMessage("Pack check is enabled but this server version doesn't support hasResourcePack! Setting is ignored.", Logger.LogLevel.WARN);

        loadPackTitles(config);
    }

    private void loadPackTitles(FileConfiguration config) {
        YamlConfiguration menus = DeluxeAuctions.getInstance().menusFile;
        if (menus == null)
            return;

        boolean changed = false;
        for (String key : menus.getKeys(false)) {
            ConfigurationSection menuSection = menus.getConfigurationSection(key);
            if (menuSection == null || !menuSection.isString("title"))
                continue;

            String path = "pack-check.guis." + key + ".to-name";
            if (!config.isSet(path)) {
                config.set(path, "");
                changed = true;
            }

            String toName = config.getString(path, "");
            if (toName != null && !toName.isEmpty())
                this.packTitles.put(key, toName);
        }

        if (!changed)
            return;

        // Saving without comment support (pre 1.18.1) would strip all config comments
        try {
            ConfigurationSection.class.getMethod("getComments", String.class);
            DeluxeAuctions.getInstance().saveConfig();
        } catch (NoSuchMethodException ignored) {}
    }

    // Whether this menu's title is being replaced because the player has the resource pack.
    // Used to skip decorative glass items in pack-themed GUIs.
    public boolean isPackApplied(Player player, String menuName) {
        if (!this.packCheckEnabled || !this.packCheckSupported || this.packTitles.isEmpty())
            return false;
        if (player == null || !player.hasResourcePack())
            return false;

        String toName = this.packTitles.get(menuName);
        return toName != null && !toName.isEmpty();
    }

    public String getPackTitle(Player player, String menuName, String title) {
        if (!isPackApplied(player, menuName))
            return title;

        return Utils.placeholderApi(player, this.packTitles.get(menuName));
    }

    public void addCustomItems(Player player, HInventory gui, ConfigurationSection section) {
        if (section == null)
            return;

        section = section.getConfigurationSection("items");
        if (section == null)
            return;

        Set<String> keys = section.getKeys(false);
        if (keys.isEmpty())
            return;

        for (String key : keys) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);

            ItemStack item = Utils.createItemFromSection(itemSection, null);
            if (item == null)
                continue;

            commands(gui, itemSection, player, item, itemSection.getStringList("left_commands"), true);
            commands(gui, itemSection, player, item, itemSection.getStringList("right_commands"), false);
        }
    }

    private void commands(HInventory gui, ConfigurationSection itemSection, Player player, ItemStack item, List<String> commands, boolean isLeft) {
        if (commands.isEmpty())
            gui.setItem(itemSection, ClickableItem.empty(item));
        else
            gui.setItem(itemSection, ClickableItem.of(item, (event) -> {
                ClickType clickType = event.getClick();
                if (!isLeft) {
                    if (!clickType.equals(ClickType.RIGHT) && !clickType.equals(ClickType.SHIFT_RIGHT))
                        return;
                } else if (clickType.equals(ClickType.RIGHT) || clickType.equals(ClickType.SHIFT_RIGHT))
                        return;

                for (String command : commands) {
                    command = command
                            .replace("%player_displayname%", player.getDisplayName())
                            .replace("%player_name%", player.getName())
                            .replace("%player_uuid%", String.valueOf(player.getUniqueId()));

                    if (command.startsWith("[close]"))
                        player.closeInventory();
                    else if (command.startsWith("[player]"))
                        player.performCommand(command
                                .replace("[player] ", "")
                                .replace("[player]", ""));
                    else
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                                .replace("[console] ", "")
                                .replace("[console]", ""));
                }
            }));
    }

    public void addNormalItems(Player player, HInventory gui, ConfigurationSection section, Category category) {
        int closeSlot = section.getInt("close");
        ItemStack close = DeluxeAuctions.getInstance().normalItems.get("close");
        if (closeSlot > 0 && close != null)
            gui.setItem(closeSlot, ClickableItem.of(close, (event) -> player.closeInventory()));

        // Skip decorative glass when the resource pack themes this menu
        if (isPackApplied(player, section.getName()))
            return;

        List<Integer> glassSlots = section.getIntegerList("glass");
        ItemStack glass = category != null ? category.getGlass() : DeluxeAuctions.getInstance().normalItems.get("glass");
        if (glass == null)
            return;
        glass = glass.clone();

        for (int i : glassSlots)
            gui.setItem(i, ClickableItem.empty(glass));
    }
    public void addNormalItems(Player player, HInventory gui, ConfigurationSection section) {
        // Skip decorative glass when the resource pack themes this menu
        if (!isPackApplied(player, section.getName())) {
            List<Integer> glassSlots = section.getIntegerList("glass");
            ItemStack glass = DeluxeAuctions.getInstance().normalItems.get("glass");
            if (glass != null && !glassSlots.isEmpty()) {
                glass = glass.clone();
                for (int i : glassSlots)
                    gui.setItem(i, ClickableItem.empty(glass));
            }
        }

        int closeSlot = section.getInt("close");
        ItemStack close = DeluxeAuctions.getInstance().normalItems.get("close");
        if (closeSlot > 0 && close != null)
            gui.setItem(closeSlot, ClickableItem.of(close, (event) -> player.closeInventory()));
    }

    public HInventory createInventory(Player player, ConfigurationSection section, String type, PlaceholderUtil placeholderUtil) {
        int size = section.getInt("size", 6);

        size = size > 6 ? size / 9 : size;
        if (size <= 0)
            size = 6;

        String title = section.getString("title");
        if (type.equalsIgnoreCase("search"))
            title = DeluxeAuctions.getInstance().menusFile.getString("auctions_menu.search.title");
        if (title == null)
            title = "&cTitle is missing in config!";

        title = getPackTitle(player, section.getName(), title);

        if (placeholderUtil != null) {
            Map<String, String> placeholders = placeholderUtil.getPlaceholders();
            if (placeholders != null && !placeholders.isEmpty())
                for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                    String key = placeholder.getKey();
                    String value = placeholder.getValue();

                    title = title
                            .replace(key, value);
                }
        }

        HInventory gui = InventoryAPI.getInventoryManager()
                .setTitle(Utils.colorize(title.length() > 32 ? title.substring(0, 29) + "..." : title))
                .setSize(size)
                .setId(type)
                .create();

        if (!section.getName().equalsIgnoreCase("auctions_menu"))
            addNormalItems(player, gui, section);

        addCustomItems(player, gui, section);
        return gui;
    }
}
