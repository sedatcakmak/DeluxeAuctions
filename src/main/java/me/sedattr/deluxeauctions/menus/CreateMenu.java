package me.sedattr.deluxeauctions.menus;

import com.google.common.util.concurrent.AtomicDouble;
import me.sedattr.auctionsapi.AuctionHook;
import me.sedattr.auctionsapi.cache.AuctionCache;
import me.sedattr.auctionsapi.cache.PlayerCache;
import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.deluxeauctions.inventoryapi.HInventory;
import me.sedattr.deluxeauctions.inventoryapi.item.ClickableItem;
import me.sedattr.deluxeauctions.managers.Auction;
import me.sedattr.deluxeauctions.managers.AuctionType;
import me.sedattr.deluxeauctions.managers.PlayerPreferences;
import me.sedattr.deluxeauctions.others.PlaceholderUtil;
import me.sedattr.deluxeauctions.others.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class CreateMenu implements MenuManager {
    private final Player player;
    private ConfigurationSection section;
    private HInventory gui;
    private final PlayerPreferences playerAuction;
    private ItemStack createItem;
    private String type;

    public CreateMenu(Player player) {
        this.player = player;
        this.playerAuction = PlayerCache.getPreferences(player.getUniqueId());
        this.section = DeluxeAuctions.getInstance().menusFile.getConfigurationSection((this.playerAuction.getCreateType().equals(AuctionType.BIN) ? "bin" : "normal") + "_auction_create_menu");
    }

    public void open(String back) {
        if (this.section == null)
            return;

        this.createItem = PlayerCache.getItem(this.player.getUniqueId());

        this.type = back;
        this.gui = DeluxeAuctions.getInstance().menuHandler.createInventory(this.player, this.section, "create", null);

        if (!back.equals("command")) {
            int goBackSlot = this.section.getInt("back");
            ItemStack goBackItem = DeluxeAuctions.getInstance().normalItems.get("go_back");

            if (goBackSlot > 0 && goBackItem != null)
                gui.setItem(goBackSlot, ClickableItem.of(goBackItem, (event) -> {
                    if (back.equals("main"))
                        AuctionHook.openMainMenu(this.player);
                    else
                        new ManageMenu(this.player).open(1, back);
                }));
        }

        loadExampleItem();
        loadPriceItem();
        loadSwitchItem();
        loadConfirmItem();
        loadTimeItem();
        loadEconomyItem();

        this.gui.open(this.player);
    }

    private void loadEconomyItem() {
        ConfigurationSection itemSection = this.section.getConfigurationSection("auction_economy");
        if (itemSection == null)
            return;

        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%economy_name%", this.playerAuction.getCreateEconomy().getName());

        ItemStack itemStack = Utils.createItemFromSection(itemSection, placeholderUtil);
        gui.setItem(itemSection.getInt("slot"), ClickableItem.of(itemStack, (event) -> new EconomyMenu(this.player).open()));
    }

    private void loadTimeItem() {
        ConfigurationSection itemSection = this.section.getConfigurationSection("auction_duration");
        if (itemSection == null)
            return;

        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%time_fee%", this.playerAuction.getCreateEconomy().getText().replace("%price%", DeluxeAuctions.getInstance().numberFormat.format(AuctionHook.calculateDurationFee(this.playerAuction.getCreateTime()))))
                .addPlaceholder("%auction_time%", DeluxeAuctions.getInstance().timeFormat.formatTime(this.playerAuction.getCreateTime(), "other_times"));

        ItemStack itemStack = Utils.createItemFromSection(itemSection, placeholderUtil);
        gui.setItem(itemSection.getInt("slot"), ClickableItem.of(itemStack, (event) -> new DurationMenu(this.player).open()));
    }

    private void loadPriceItem() {
        ConfigurationSection itemSection = this.section.getConfigurationSection("select_price");
        if (itemSection == null)
            return;

        double priceFeePercent = AuctionHook.calculatePriceFeePercent(this.playerAuction.getCreatePrice(), this.playerAuction.getCreateType().equals(AuctionType.BIN) ? "bin" : "normal");
        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%auction_price%", this.playerAuction.getCreateEconomy().getText().replace("%price%", DeluxeAuctions.getInstance().numberFormat.format(this.playerAuction.getCreatePrice())))
                .addPlaceholder("%price_fee%", this.playerAuction.getCreateEconomy().getText().replace("%price%", DeluxeAuctions.getInstance().numberFormat.format(this.playerAuction.getCreatePrice()/100*priceFeePercent)))
                .addPlaceholder("%price_fee_percent%", DeluxeAuctions.getInstance().numberFormat.format(priceFeePercent));

        ItemStack itemStack = Utils.createItemFromSection(itemSection, placeholderUtil);
        gui.setItem(itemSection.getInt("slot"), ClickableItem.of(itemStack, (event) -> DeluxeAuctions.getInstance().inputMenu.open(this.player, this)));
    }

    private void loadSwitchItem() {
        ConfigurationSection itemSection = this.section.getConfigurationSection("switch_type");
        if (itemSection == null)
            return;

        ItemStack itemStack = Utils.createItemFromSection(itemSection, null);
        gui.setItem(itemSection.getInt("slot"), ClickableItem.of(itemStack, (event) -> {
            AuctionType auctionType = this.playerAuction.getCreateType().equals(AuctionType.BIN) ? AuctionType.NORMAL : AuctionType.BIN;

            if (AuctionHook.isAuctionTypeDisabled(auctionType.name()))
                Utils.sendMessage(player, "disabled_auction_type", new PlaceholderUtil()
                        .addPlaceholder("%auction_type%", auctionType.name()));
            else {
                this.playerAuction.setCreateType(auctionType);
                this.section = DeluxeAuctions.getInstance().menusFile.getConfigurationSection((this.playerAuction.getCreateType().equals(AuctionType.BIN) ? "bin" : "normal") + "_auction_create_menu");
            }

            open(this.type);
        }));
    }

    private void loadConfirmItem() {
        ConfigurationSection itemSection = this.createItem != null ? this.section.getConfigurationSection("confirm_auction.with_item") : this.section.getConfigurationSection("confirm_auction.without_item");
        if (itemSection == null)
            return;

        double priceFeePercent = AuctionHook.calculatePriceFeePercent(this.playerAuction.getCreatePrice(), this.playerAuction.getCreateType().equals(AuctionType.BIN) ? "bin" : "normal");
        AtomicDouble totalFee = new AtomicDouble(0.0);
        if (priceFeePercent > 0.0)
            totalFee.addAndGet(this.playerAuction.getCreatePrice()/100*priceFeePercent);

        double durationFee = AuctionHook.calculateDurationFee(this.playerAuction.getCreateTime());
        if (durationFee > 0.0)
            totalFee.addAndGet(durationFee);

        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%auction_fee%", this.playerAuction.getCreateEconomy().getText().replace("%price%", DeluxeAuctions.getInstance().numberFormat.format(totalFee.get())))
                .addPlaceholder("%auction_price%", this.playerAuction.getCreateEconomy().getText().replace("%price%", DeluxeAuctions.getInstance().numberFormat.format(this.playerAuction.getCreatePrice())))
                .addPlaceholder("%item_displayname%", Utils.getDisplayName(this.createItem))
                .addPlaceholder("%item_name%", Utils.strip(Utils.getDisplayName(this.createItem)))
                .addPlaceholder("%player_displayname%", this.player.getDisplayName())
                .addPlaceholder("%player_name%", this.player.getName())
                .addPlaceholder("%auction_time%", DeluxeAuctions.getInstance().timeFormat.formatTime(this.playerAuction.getCreateTime(), "other_times"));

        ItemStack itemStack = Utils.createItemFromSection(itemSection, placeholderUtil);
        this.createItem = PlayerCache.getItem(this.player.getUniqueId());

        if (this.createItem == null)
            gui.setItem(itemSection.getInt("slot"), ClickableItem.empty(itemStack));
        else
            gui.setItem(itemSection.getInt("slot"), ClickableItem.of(itemStack, (event) -> {
                double balance = this.playerAuction.getCreateEconomy().getManager().getBalance(this.player);
                if (balance < totalFee.get()) {
                    Utils.playSound(this.player, "not_enough_money");
                    Utils.sendMessage(this.player, "not_enough_money", placeholderUtil.addPlaceholder("%required_money%", this.playerAuction.getCreateEconomy().getText().replace("%price%", DeluxeAuctions.getInstance().numberFormat.format(totalFee.get()-balance))));
                    return;
                }

                if (Utils.hasPermission(player, "bypass", "permission")) {
                    if (AuctionCache.getOwnedAuctions(this.player.getUniqueId()).size() >= AuctionHook.getLimit(this.player, "auction_limit")) {
                        Utils.sendMessage(this.player, "reached_auction_limit");
                        return;
                    }

                    AuctionType createType = playerAuction.getCreateType();
                    String type = playerAuction.getCreateType().equals(AuctionType.BIN) ? "bin" : "normal";

                    Auction newAuction = new Auction(playerAuction.getCreateEconomy(), playerAuction.getCreatePrice(), createType, playerAuction.getCreateTime());
                    if (newAuction.create(this.player, totalFee.get())) {
                        placeholderUtil
                                .addPlaceholder("%auction_type%", createType.name())
                                .addPlaceholder("%auction_uuid%", String.valueOf(newAuction.getAuctionUUID()));

                        Utils.sendMessage(this.player, "created_" + type + "_auction", placeholderUtil);
                        Utils.broadcastMessage(this.player, type + "_auction_broadcast", placeholderUtil);

                        if (DeluxeAuctions.getInstance().discordWebhook != null)
                            DeluxeAuctions.getInstance().discordWebhook.sendMessage("create_auction", placeholderUtil);

                        if (createType == AuctionType.BIN)
                            new BinViewMenu(player, newAuction).open("manage");
                        else
                            new NormalViewMenu(player, newAuction).open("manage");
                    }

                    return;
                }

                new ConfirmMenu(this.player, "confirm_auction").setPrice(totalFee.get()).open();
            }));
    }

    private void loadExampleItem() {
        ConfigurationSection exampleSection = this.createItem == null ? this.section.getConfigurationSection("example_item.without_item") : this.section.getConfigurationSection("example_item.with_item");
        if (exampleSection == null)
            return;

        ItemStack example;
        if (this.createItem == null) {
            example = Utils.createItemFromSection(exampleSection, null);
            if (example == null)
                return;

            int slot = exampleSection.getInt("slot");
            this.gui.setItem(slot, ClickableItem.empty(example));
        } else if (PlayerCache.getItem(this.player.getUniqueId()) != null) {
            example = this.createItem.clone();
            ItemMeta meta = example.getItemMeta();
            if (meta == null)
                return;

            String displayName = exampleSection.getString("name");
            if (displayName != null)
                meta.setDisplayName(Utils.colorize(displayName
                    .replace("%item_name%", Utils.getDisplayName(this.createItem))));

            List<String> lore = exampleSection.getStringList("lore");
            List<String> newLore = new ArrayList<>();
            if (!lore.isEmpty())
                for (String line : lore) {
                    if (line.contains("%item_lore%")) {
                        List<String> itemLore = meta.getLore();
                        if (itemLore != null && !itemLore.isEmpty())
                            newLore.addAll(itemLore);

                        continue;
                    }

                    newLore.add(Utils.colorize(line
                            .replace("%item_name%", Utils.getDisplayName(this.createItem))
                    ));
                }

            meta.setLore(newLore);
            example.setItemMeta(meta);

            int slot = exampleSection.getInt("slot");
            this.gui.setItem(slot, ClickableItem.of(example, (event) -> {
                boolean status = playerAuction.updateCreateItem(player, -1, true);
                if (!status)
                    return;

                this.createItem = null;

                loadExampleItem();
                loadConfirmItem();
                loadPriceItem();
                loadTimeItem();
            }));
        }
    }

    @Override
    public void inputResult(String input) {
        double number;
        double reversedPrice = DeluxeAuctions.getInstance().numberFormat.reverseFormat(input);
        if (reversedPrice > 0)
            number = reversedPrice;
        else {
            try {
                number = Double.parseDouble(input);
            } catch (Exception e) {
                number = 0;
            }
        }

        if (number <= 0 || !Double.isFinite(number))
            Utils.sendMessage(this.player, "wrong_price");
        else {
            double limit = AuctionHook.getPriceLimit(player, "price_limit");
            if (number > limit)
                Utils.sendMessage(player, "reached_price_limit", new PlaceholderUtil()
                        .addPlaceholder("%price_limit%", this.playerAuction.getCreateEconomy().getText().replace("%price%",  DeluxeAuctions.getInstance().numberFormat.format(limit))));

            this.playerAuction.setCreatePrice(Math.min(number, limit));
        }

        open(this.type);
    }

    @Override
    public String getMenuName() {
        return "create";
    }
}
