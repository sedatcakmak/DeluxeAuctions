package me.sedattr.deluxeauctions.menus;

import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.auctionsapi.AuctionHook;
import me.sedattr.auctionsapi.cache.PlayerCache;
import me.sedattr.deluxeauctions.inventoryapi.HInventory;
import me.sedattr.deluxeauctions.inventoryapi.item.ClickableItem;
import me.sedattr.deluxeauctions.managers.PlayerPreferences;
import me.sedattr.deluxeauctions.others.PlaceholderUtil;
import me.sedattr.deluxeauctions.others.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class DurationMenu implements MenuManager {
    private final Player player;
    private final ConfigurationSection section;
    private HInventory gui;
    private final PlayerPreferences playerAuction;
    private boolean isHours = true;

    public DurationMenu(Player player) {
        this.player = player;
        this.section = DeluxeAuctions.getInstance().menusFile.getConfigurationSection("duration_menu");
        this.playerAuction = PlayerCache.getPreferences(player.getUniqueId());
    }

    public void open() {
        this.gui = DeluxeAuctions.getInstance().menuHandler.createInventory(this.player, this.section, "duration", null);

        int goBackSlot = this.section.getInt("back");
        ItemStack goBackItem = DeluxeAuctions.getInstance().normalItems.get("go_back");
        if (goBackSlot > 0 && goBackItem != null)
            gui.setItem(goBackSlot, ClickableItem.of(goBackItem, (event) -> new CreateMenu(this.player).open("main")));

        loadDurationItems();
        loadCustomDuration();

        this.gui.open(this.player);
    }

    private void loadDurationItems() {
        ConfigurationSection durationsSection = this.section.getConfigurationSection("durations");
        if (durationsSection == null)
            return;

        for (String key : durationsSection.getKeys(false)) {
            ConfigurationSection durationSection = durationsSection.getConfigurationSection(key);
            if (durationSection == null)
                return;

            int time = durationSection.getInt("duration", 86400);
            PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                    .addPlaceholder("%duration_fee%", this.playerAuction.getCreateEconomy().getText().replace("%price%", DeluxeAuctions.getInstance().numberFormat.format(AuctionHook.calculateDurationFee(time, this.playerAuction.getCreateEconomy()))));

            ItemStack itemStack = Utils.createItemFromSection(durationSection, placeholderUtil);
            if (itemStack == null)
                return;

            int slot = durationSection.getInt("slot");
            if (this.playerAuction.getCreateTime() == time) {
                if (DeluxeAuctions.getInstance().version >= 21) {
                    ItemMeta meta = itemStack.getItemMeta();
                    if (meta == null)
                        return;

                    meta.addEnchant(Enchantment.SILK_TOUCH, 1, true);
                    itemStack.setItemMeta(meta);
                } else
                    itemStack.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);

                ItemMeta meta = itemStack.getItemMeta();
                if (meta == null)
                    return;

                meta.addItemFlags(ItemFlag.values());
                itemStack.setItemMeta(meta);

                this.gui.setItem(slot, ClickableItem.empty(itemStack));
            } else {
                this.gui.setItem(slot, ClickableItem.of(itemStack, (event) -> {
                    int limit = AuctionHook.getLimit(this.player, "duration_limit");
                    if (time > limit)
                        Utils.sendMessage(this.player, "reached_duration_limit", new PlaceholderUtil()
                                .addPlaceholder("%duration_limit%", DeluxeAuctions.getInstance().timeFormat.formatTime((long) limit, "other_times")));

                    this.playerAuction.setCreateTime(Math.min(limit, time));
                    loadDurationItems();
                }));
            }
        }
    }

    private void loadCustomDuration() {
        ConfigurationSection itemSection = this.section.getConfigurationSection("custom_duration");
        if (itemSection == null)
            return;

        ItemStack item = Utils.createItemFromSection(itemSection, null);
        if (item == null)
            return;

        int slot = itemSection.getInt("slot");
        this.gui.setItem(slot, ClickableItem.of(item, (event) -> {
            ClickType clickType = event.getClick();
            this.isHours = clickType != ClickType.RIGHT && clickType != ClickType.SHIFT_RIGHT;

            DeluxeAuctions.getInstance().inputMenu.open(this.player, this);
        }));
    }

    @Override
    public void inputResult(String input) {
        int number;
        try {
            number = Integer.parseInt(input);
        } catch (Exception e) {
            number = 0;
        }

        int time = this.isHours ? number * 3600 : number * 60;
        if (time <= 0)
            Utils.sendMessage(this.player, "wrong_duration");
        else {
            int limit = AuctionHook.getLimit(this.player, "duration_limit");
            if (time > limit)
                Utils.sendMessage(this.player, "reached_duration_limit", new PlaceholderUtil()
                        .addPlaceholder("%duration_limit%", DeluxeAuctions.getInstance().timeFormat.formatTime((long) limit, "other_times")));

            this.playerAuction.setCreateTime(Math.min(time, limit));
        }

        new CreateMenu(this.player).open("main");
    }

    @Override
    public String getMenuName() {
        return "duration";
    }
}
