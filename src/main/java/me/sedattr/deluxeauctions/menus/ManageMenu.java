package me.sedattr.deluxeauctions.menus;

import me.sedattr.auctionsapi.AuctionHook;
import me.sedattr.auctionsapi.cache.AuctionCache;
import me.sedattr.auctionsapi.cache.PlayerCache;
import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.deluxeauctions.inventoryapi.HInventory;
import me.sedattr.deluxeauctions.inventoryapi.item.ClickableItem;
import me.sedattr.deluxeauctions.managers.*;
import me.sedattr.deluxeauctions.others.PlaceholderUtil;
import me.sedattr.deluxeauctions.others.TaskUtils;
import me.sedattr.deluxeauctions.others.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ManageMenu {
    private final Player player;
    private final ConfigurationSection section;
    private HInventory gui;
    private boolean itemUpdater = false;
    private final PlayerPreferences playerAuction;
    private final List<Auction> auctions;
    private int page;
    private SortType sortType = SortType.valueOf(DeluxeAuctions.getInstance().configFile.getString("settings.default_sort_type"));
    private String back;

    public ManageMenu(Player player) {
        this.player = player;
        this.section = DeluxeAuctions.getInstance().menusFile.getConfigurationSection("manage_auctions");
        this.playerAuction = PlayerCache.getPreferences(player.getUniqueId());
        this.auctions = AuctionCache.getOwnedAuctions(this.player.getUniqueId());
    }

    public void open(int page, String back) {
        this.page = page;
        this.back = back;

        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%current_page%", String.valueOf(page))
                .addPlaceholder("%total_page%", String.valueOf(getTotalPage()));

        this.gui = DeluxeAuctions.getInstance().menuHandler.createInventory(this.player, this.section, "manage", placeholderUtil);

        if (!back.equals("command")) {
            int goBackSlot = this.section.getInt("back");
            ItemStack goBackItem = DeluxeAuctions.getInstance().normalItems.get("go_back");
            if (goBackSlot > 0 && goBackItem != null)
                gui.setItem(goBackSlot, ClickableItem.of(goBackItem, (event) -> {
                    switch (back) {
                        case "main":
                            AuctionHook.openMainMenu(this.player);
                            return;
                        case "auctions":
                            new AuctionsMenu(this.player).open(this.playerAuction.getCategory().getName(), this.playerAuction.getPage());
                    }
                }));
        }

        loadAuctions();
        loadCreateAuctionItem();
        loadSorterItem();
        loadClaimAll();

        if (this.section.getBoolean("pagination")) {
            if (getTotalPage() > page) {
                ItemStack nextPage = Utils.createItemFromSection(this.section.getConfigurationSection("next_page"), placeholderUtil);
                if (nextPage != null) {
                    this.gui.setItem(this.section.getInt("next_page.slot"), ClickableItem.of(nextPage, (event -> {
                        ClickType clickType = event.getClick();
                        if (clickType.equals(ClickType.RIGHT) || clickType.equals(ClickType.SHIFT_RIGHT))
                            open(getTotalPage(), back);
                        else
                            open(page+1, back);
                    })));
                }
            }

            if (page > 1) {
                ItemStack previousPage = Utils.createItemFromSection(this.section.getConfigurationSection("previous_page"), placeholderUtil);
                if (previousPage != null)
                    this.gui.setItem(this.section.getInt("previous_page.slot"), ClickableItem.of(previousPage, (event -> {
                        ClickType clickType = event.getClick();
                        if (clickType.equals(ClickType.RIGHT) || clickType.equals(ClickType.SHIFT_RIGHT))
                            open(1, back);
                        else
                            open(page-1, back);
                    })));
            }
        }

        this.gui.open(this.player);
        updateItems();
    }

    private void loadSorterItem() {
        ItemStack sorterItem = Utils.createItemFromSection(this.section.getConfigurationSection("auction_sorter"), null);
        List<String> lore = this.section.getStringList("auction_sorter.lore." + sortType.name().toLowerCase(Locale.ENGLISH));
        List<String> newLore = new ArrayList<>();
        if (!lore.isEmpty())
            for (String line : lore)
                newLore.add(Utils.colorize(line));

        Utils.changeLore(sorterItem, newLore, null);

        int slot = this.section.getInt("auction_sorter.slot");
        this.gui.setItem(slot, ClickableItem.of(sorterItem, event -> {
            ClickType clickType = event.getClick();
            Utils.playSound(player, "sorter_item_click");

            List<String> types = this.section.getStringList("auction_sorter.types");
            int currentType = !types.isEmpty() && types.contains(sortType.name()) ? types.indexOf(sortType.name()) : 0;

            // backwards
            if (!types.isEmpty()) {
                if (clickType.equals(ClickType.RIGHT) || clickType.equals(ClickType.SHIFT_RIGHT)) {
                    currentType--;
                    if (currentType < 0)
                        currentType = types.size()-1;
                }
                else {
                    currentType++;
                    if (currentType >= types.size())
                        currentType = 0;
                }
            }

            String newType = types.get(currentType);
            this.sortType = SortType.valueOf(newType.toUpperCase(Locale.ENGLISH));

            loadAuctions();
            loadSorterItem();
        }));
    }

    private int getTotalPage() {
        List<Integer> slots = this.section.getIntegerList("slots");

        int totalPage = this.auctions.size() / slots.size() + 1;
        if (this.auctions.size() <= slots.size()*(totalPage-1))
            totalPage--;
        if (this.auctions.isEmpty())
            totalPage=1;

        return totalPage;
    }

    private void loadClaimAll() {
        ConfigurationSection itemSection = this.section.getConfigurationSection("claim_all");
        if (itemSection == null)
            return;

        Map<String, Double> moneyByEconomy = new HashMap<>();
        double totalMoney = 0;
        int item = 0;
        for (Auction auction : this.auctions) {
            if (!auction.isEnded())
                continue;

            PlayerBid playerBid = auction.getAuctionBids().getHighestBid();
            if (playerBid == null) {
                item++;
                continue;
            }

            String economyKey = auction.getEconomy() != null ? auction.getEconomy().getKey() : "";
            moneyByEconomy.merge(economyKey, playerBid.getBidPrice(), Double::sum);
            totalMoney += playerBid.getBidPrice();
        }

        if (totalMoney == 0.0 && item == 0) {
            ConfigurationSection claimSection = itemSection.getConfigurationSection("without_claimable");

            ItemStack itemStack = Utils.createItemFromSection(claimSection, null);
            if (itemStack == null)
                return;

            this.gui.setItem(claimSection.getInt("slot"), ClickableItem.empty(itemStack));
        } else {
            ConfigurationSection claimSection = itemSection.getConfigurationSection("with_claimable");
            PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                    .addPlaceholder("%claimable_items%", String.valueOf(item))
                    .addPlaceholder("%claimable_money%", DeluxeAuctions.getInstance().numberFormat.format(totalMoney));

            // Per-economy collectable balance: %claimable_money_<economyKey>% for each defined economy
            for (Economy economy : DeluxeAuctions.getInstance().economies.values())
                placeholderUtil.addPlaceholder("%claimable_money_" + economy.getKey() + "%", DeluxeAuctions.getInstance().numberFormat.format(moneyByEconomy.getOrDefault(economy.getKey(), 0.0)));

            ItemStack itemStack = Utils.createItemFromSection(claimSection, placeholderUtil);
            if (itemStack == null)
                return;

            // Hide lines whose collectable amount is 0 (dynamic, per economy)
            List<String> claimableLore = new ArrayList<>();
            for (String line : claimSection.getStringList("lore")) {
                if (line.contains("%claimable_items%") && item == 0)
                    continue;
                if (line.contains("%claimable_money%") && totalMoney <= 0.0)
                    continue;

                boolean hide = false;
                for (Economy economy : DeluxeAuctions.getInstance().economies.values())
                    if (line.contains("%claimable_money_" + economy.getKey() + "%") && moneyByEconomy.getOrDefault(economy.getKey(), 0.0) <= 0.0) {
                        hide = true;
                        break;
                    }
                if (hide)
                    continue;

                claimableLore.add(line);
            }
            Utils.changeLore(itemStack, claimableLore, placeholderUtil);

            this.gui.setItem(claimSection.getInt("slot"), ClickableItem.of(itemStack, (event) -> {
                this.player.closeInventory();
                playerAuction.collectAuctions(this.player);
            }));
        }
    }

    private void loadAuctions() {
        List<Integer> slots = this.section.getIntegerList("slots");
        if (slots.isEmpty())
            return;

        int lower = (this.page - 1) * slots.size();
        int upper = Math.min(this.page * slots.size(), this.auctions.size());

        List<Auction> newAuctions = new ArrayList<>(upper - lower);
        for (int i = lower; i < upper; i++) {
            Auction auction = this.auctions.get(i);
            newAuctions.add(auction);
        }

        if (this.sortType.equals(SortType.HIGHEST_PRICE))
            newAuctions.sort(Comparator.comparing(Auction::getAuctionPrice).reversed());
        else if (this.sortType.equals(SortType.LOWEST_PRICE))
            newAuctions.sort(Comparator.comparing(Auction::getAuctionPrice));
        else if (this.sortType.equals(SortType.RANDOM))
            Collections.shuffle(newAuctions);
        else if (this.sortType.equals(SortType.ENDING_SOON))
            newAuctions.sort(Comparator.comparing(Auction::getAuctionEndTime));

        int i = 0;
        for (Auction auction : newAuctions) {
            ItemStack itemStack = AuctionHook.getUpdatedAuctionItem(auction);

            int slot = i >= slots.size() ? 0 : slots.get(i);
            this.gui.setItem(slot, ClickableItem.of(itemStack, (event) -> {
                if (auction.getAuctionType() == AuctionType.BIN)
                    new BinViewMenu(this.player, auction).open("manage");
                else
                    new NormalViewMenu(this.player, auction).open("manage");
            }));

            i++;
        }
    }

    private void loadCreateAuctionItem() {
        ConfigurationSection itemSection = this.section.getConfigurationSection("create");
        if (itemSection == null)
            return;

        ItemStack item = Utils.createItemFromSection(itemSection, null);
        if (item == null)
            return;

        int slot = itemSection.getInt("slot");
        this.gui.setItem(slot, ClickableItem.of(item, (event) -> {
            new CreateMenu(this.player).open(this.back);
        }));
    }

    private void updateItems() {
        if (this.itemUpdater)
            return;

        this.itemUpdater = true;
        Runnable runnable = this::loadAuctions;
        TaskUtils.runTimerAsync(this.player, "manage", runnable, 20, 20);
    }
}
