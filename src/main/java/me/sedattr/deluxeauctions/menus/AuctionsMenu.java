package me.sedattr.deluxeauctions.menus;

import me.sedattr.auctionsapi.AuctionHook;
import me.sedattr.auctionsapi.cache.AuctionCache;
import me.sedattr.auctionsapi.cache.CategoryCache;
import me.sedattr.auctionsapi.cache.PlayerCache;
import me.sedattr.deluxeauctions.inventoryapi.HInventory;
import me.sedattr.deluxeauctions.inventoryapi.item.ClickableItem;
import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.deluxeauctions.managers.*;
import me.sedattr.deluxeauctions.others.PlaceholderUtil;
import me.sedattr.deluxeauctions.others.TaskUtils;
import me.sedattr.deluxeauctions.others.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class AuctionsMenu implements MenuManager {
    private final Player player;
    private final PlayerPreferences playerAuction;
    private final ConfigurationSection section;

    private HInventory gui;

    private List<Auction> currentAuctions = new ArrayList<>();
    private List<Auction> filteredAuctions = new ArrayList<>();

    private int totalPage;

    private int updaterEpoch = 0;
    private List<Integer> slots;

    public AuctionsMenu(Player player) {
        this.player = player;
        this.section = DeluxeAuctions.getInstance().menusFile.getConfigurationSection("auctions_menu");
        this.playerAuction = PlayerCache.getPreferences(player.getUniqueId());
    }

    public void open(String categoryName, int page) {
        this.updaterEpoch++;
        page = Math.max(page, 1);

        if (!categoryName.equalsIgnoreCase("search")) {
            Category category = CategoryCache.getCategories().get(categoryName);
            if (category == null)
                return;

            this.playerAuction.setCategory(category);
            this.slots = DeluxeAuctions.getInstance().categoriesFile.getIntegerList(categoryName + ".slots");
        }

        this.playerAuction.setPage(page);

        TaskUtils.runAsync(() -> {
            updateTotalPage();

            this.gui = DeluxeAuctions.getInstance().menuHandler.createInventory(this.player, this.section, this.playerAuction.getSearch().isEmpty() ? "auctions" : "search", createPlaceholderUtil());
            DeluxeAuctions.getInstance().menuHandler.addNormalItems(this.player, this.gui, this.section, this.playerAuction.getCategory());

            loadSearchItem();
            loadCategories();
            loadCategoriesItem();
            loadFilterItem();
            loadSorterItem();
            loadPageItems();
            loadResetItem();
            loadRaritySorter();
            loadBidsItem();
            loadManageItem();
            loadCreateAuctionItem();
            loadStatsItem();

            boolean status = DeluxeAuctions.getInstance().configFile.getBoolean("settings.enable_main_menu", true);
            if (status) {
                int goBackSlot = this.section.getInt("back");
                ItemStack goBackItem = DeluxeAuctions.getInstance().normalItems.get("go_back");
                if (goBackSlot > 0 && goBackItem != null)
                    gui.setItem(goBackSlot, ClickableItem.of(goBackItem, (event) ->
                            AuctionHook.openMainMenu(this.player)));
            }

            loadItems();

            TaskUtils.run(() -> {
                this.gui.open(this.player);
                updateItems();
            });
        });
    }

    private void updateTotalPage() {
        // Drop a currency filter that points to a now-disabled economy
        if (!this.playerAuction.getEconomyFilter().equals("all") && !DeluxeAuctions.getInstance().economies.containsKey(this.playerAuction.getEconomyFilter()))
            this.playerAuction.setEconomyFilter("all");

        this.filteredAuctions = AuctionCache.getFilteredAuctions(this.playerAuction.getAuctionType(), this.playerAuction.getRarityType(), this.playerAuction.getCategory(), this.playerAuction.getSearch(), this.playerAuction.getEconomyFilter());
        this.currentAuctions = AuctionCache.getOnGoingAuctions(this.filteredAuctions, this.playerAuction.getSortType(), this.playerAuction.getPage(), this.slots.size());

        int auctionAmount = this.filteredAuctions.size();

        int totalPage = auctionAmount / this.slots.size() + 1;
        if (auctionAmount <= this.slots.size()*(totalPage-1))
            totalPage--;

        this.totalPage = Math.max(totalPage, 1);
    }

    private PlaceholderUtil createPlaceholderUtil() {
        return new PlaceholderUtil()
                .addPlaceholder("%search%", Utils.colorize(String.valueOf(this.playerAuction.getSearch())))
                .addPlaceholder("%current_page%", String.valueOf(this.playerAuction.getPage()))
                .addPlaceholder("%total_page%", String.valueOf(this.totalPage));
    }

    private void loadStatsItem() {
        ConfigurationSection itemSection = this.section.getConfigurationSection("stats");
        if (itemSection == null)
            return;

        ItemStack item = Utils.createItemFromSection(itemSection, null);
        if (item == null)
            return;

        this.gui.setItem(itemSection, ClickableItem.of(item, (event) -> new StatsMenu(this.player).open()));
    }

    private void loadCreateAuctionItem() {
        ConfigurationSection itemSection = this.section.getConfigurationSection("create");
        if (itemSection == null)
            return;

        ItemStack item = Utils.createItemFromSection(itemSection, null);
        if (item == null)
            return;

        this.gui.setItem(itemSection, ClickableItem.of(item, (event) -> new CreateMenu(this.player).open("main")));
    }

    private void loadManageItem() {
        ConfigurationSection itemSection = this.section.getConfigurationSection("manage");
        if (itemSection == null)
            return;

        ItemStack item = Utils.createItemFromSection(itemSection, null);
        if (item == null)
            return;

        List<Auction> ownedAuctions = AuctionCache.getOwnedAuctions(this.player.getUniqueId());
        String type;

        int endedAmount = 0;
        int itemAmount = 0;
        double moneyAmount = 0.0;
        for (Auction auction : ownedAuctions) {
            if (!auction.isEnded())
                continue;

            endedAmount++;
            PlayerBid playerBid = auction.getAuctionBids().getHighestBid();
            if (playerBid == null)
                itemAmount++;
            else
                moneyAmount+=playerBid.getBidPrice();
        }
        if (ownedAuctions.isEmpty())
            type = "without_auctions";
        else {
            if (itemAmount > 0 || moneyAmount > 0)
                type = "collectable";
            else
                type = "with_auctions";
        }

        List<String> lore = itemSection.getStringList("lore." + type);
        if (!lore.isEmpty()) {
            PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                    .addPlaceholder("%total_auction_amount%", String.valueOf(ownedAuctions.size()))
                    .addPlaceholder("%collectable_money_amount%", DeluxeAuctions.getInstance().numberFormat.format(moneyAmount))
                    .addPlaceholder("%collectable_item_amount%", String.valueOf(itemAmount))
                    .addPlaceholder("%ended_auction_amount%", String.valueOf(endedAmount));

            Utils.changeLore(item, lore, placeholderUtil);
        }

        this.gui.setItem(itemSection, ClickableItem.of(item, (event) -> new ManageMenu(this.player).open(1, "auctions")));
    }

    private void loadBidsItem() {
        ConfigurationSection itemSection = this.section.getConfigurationSection("bids");
        if (itemSection == null)
            return;

        ItemStack item = Utils.createItemFromSection(itemSection, null);
        if (item == null)
            return;

        List<Auction> bidAuctions = AuctionCache.getBidAuctions(this.player.getUniqueId());
        String type;

        int collectableAmount = 0;
        int topBidAmount = 0;
        int itemAmount = 0;
        double moneyAmount = 0.0;
        for (Auction auction : bidAuctions) {
            PlayerBid playerBid = auction.getAuctionBids().getHighestBid();
            if (playerBid == null)
                continue;

            if (auction.isEnded()) {
                if (playerBid.getBidOwner().equals(this.player.getUniqueId()))
                    itemAmount++;
                else
                    moneyAmount+=auction.getAuctionBids().getPlayerBid(this.player.getUniqueId()).getBidPrice();

                collectableAmount++;
                continue;
            }

            if (playerBid.getBidOwner().equals(this.player.getUniqueId()))
                topBidAmount++;
        }
        int totalBidAmount = bidAuctions.size() - collectableAmount;

        if (bidAuctions.isEmpty())
            type = "no_bids";
        else {
            if (itemAmount > 0 || moneyAmount > 0) {
                if (totalBidAmount > 0)
                    type = "collectable_with_bids";
                else
                    type = "collectable_without_bids";
            } else
                type = "bids";
        }

        List<String> lore = itemSection.getStringList("lore." + type);
        if (!lore.isEmpty()) {
            PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                    .addPlaceholder("%collectable_money_amount%", DeluxeAuctions.getInstance().numberFormat.format(moneyAmount))
                    .addPlaceholder("%collectable_item_amount%", String.valueOf(itemAmount))
                    .addPlaceholder("%total_bid_amount%", String.valueOf(totalBidAmount))
                    .addPlaceholder("%top_bid_amount%", String.valueOf(topBidAmount));

            Utils.changeLore(item, lore, placeholderUtil);
        }

        this.gui.setItem(itemSection, ClickableItem.of(item, (event) -> new BidsMenu(this.player).open(1, "auctions")));
    }

    private void loadPageItems() {
        PlaceholderUtil placeholderUtil = createPlaceholderUtil();

        if (totalPage > this.playerAuction.getPage()) {
            ConfigurationSection nextSection = this.section.getConfigurationSection("next_page");
            if (nextSection != null) {
                ItemStack nextPage = Utils.createItemFromSection(nextSection, placeholderUtil);
                if (nextPage != null) {
                    this.gui.setItem(nextSection, ClickableItem.of(nextPage, (event -> {
                        ClickType clickType = event.getClick();
                        if (clickType.equals(ClickType.RIGHT) || clickType.equals(ClickType.SHIFT_RIGHT))
                            open(this.playerAuction.getCategory().getName(), this.totalPage);
                        else
                            open(this.playerAuction.getCategory().getName(), this.playerAuction.getPage()+1);
                    })));
                }
            }
        }

        if (this.playerAuction.getPage() > 1) {
            ConfigurationSection previousSection = this.section.getConfigurationSection("previous_page");
            if (previousSection != null) {
                ItemStack previousPage = Utils.createItemFromSection(previousSection, placeholderUtil);
                if (previousPage != null)
                    this.gui.setItem(previousSection, ClickableItem.of(previousPage, (event -> {
                        ClickType clickType = event.getClick();
                        if (clickType.equals(ClickType.RIGHT) || clickType.equals(ClickType.SHIFT_RIGHT))
                            open(this.playerAuction.getCategory().getName(), 1);
                        else
                            open(this.playerAuction.getCategory().getName(), this.playerAuction.getPage() - 1);
                    })));
            }
        }
    }

    private void loadResetItem() {
        ConfigurationSection resetSection = this.section.getConfigurationSection("reset_settings");
        if (resetSection == null)
            return;
        int slot = resetSection.getInt("slot");

        if (hasResetSettings()) {
            ItemStack reset = Utils.createItemFromSection(resetSection, null);
            gui.setItem(slot, ClickableItem.of(reset, (event) -> resetSettings()));
        } else {
            handleEmptyResetSlot(slot);
        }
    }

    private void resetSettings() {
        this.playerAuction.setSearch("");
        this.playerAuction.setAuctionType(DeluxeAuctions.getInstance().auctionType);
        this.playerAuction.setSortType(DeluxeAuctions.getInstance().sortType);
        this.playerAuction.setRarityType(DeluxeAuctions.getInstance().rarityType);
        this.playerAuction.setEconomyFilter("all");

        open(this.playerAuction.getCategory().getName(), 1);
    }

    // Resource pack themed menus drop decorative glass fillers
    private boolean isPackApplied() {
        return DeluxeAuctions.getInstance().menuHandler.isPackApplied(this.player, this.section.getName());
    }

    private void handleEmptyResetSlot(int slot) {
        if (!isPackApplied() && this.section.getIntegerList("glass").contains(slot))
            gui.setItem(slot, ClickableItem.empty(this.playerAuction.getCategory().getGlass()));
        else
            gui.setItem(slot, null);
    }

    private boolean hasResetSettings() {
        return !this.playerAuction.getSearch().isEmpty() ||
                !this.playerAuction.getAuctionType().equals(DeluxeAuctions.getInstance().auctionType) ||
                !this.playerAuction.getSortType().equals(DeluxeAuctions.getInstance().sortType) ||
                !this.playerAuction.getEconomyFilter().equals("all");
    }

    private void loadSearchItem() {
        ConfigurationSection searchSection = this.section.getConfigurationSection("search");
        if (searchSection != null && searchSection.getBoolean("enabled")) {
            ItemStack search = Utils.createItemFromSection(searchSection, null);
            if (search != null) {
                updateSearchItemLore(search, searchSection);

                gui.setItem(searchSection, ClickableItem.of(search, event -> handleSearchClick(event.getClick())));
            }
        }
    }

    private void updateSearchItemLore(ItemStack search, ConfigurationSection searchSection) {
        if (!this.playerAuction.getSearch().equalsIgnoreCase("")) {
            Utils.changeLore(search, searchSection.getStringList("lore.selected"), new PlaceholderUtil()
                    .addPlaceholder("%searched%", this.playerAuction.getSearch())
                    .addPlaceholder("%found_items%", String.valueOf(this.filteredAuctions.size())));
        } else
            Utils.changeLore(search, searchSection.getStringList("lore.not_selected"), null);
    }

    private void handleSearchClick(ClickType clickType) {
        if (this.playerAuction.getSearch().equalsIgnoreCase("") || (clickType != ClickType.RIGHT && clickType != ClickType.SHIFT_RIGHT))
            DeluxeAuctions.getInstance().inputMenu.open(this.player, this);
        else {
            this.playerAuction.setSearch("");
            open(this.playerAuction.getCategory().getName(), this.playerAuction.getPage());
        }
    }

    private void loadCategoriesItem() {
        List<String> categories = DeluxeAuctions.getInstance().categoriesFile.getKeys(false).stream().toList();
        if (categories.isEmpty())
            return;

        ConfigurationSection categoriesSection = this.section.getConfigurationSection("categories");
        if (categoriesSection == null)
            return;
        ItemStack categoriesItem = Utils.createItemFromSection(categoriesSection, null);
        if (categoriesItem == null)
            return;

        List<String> lore = this.section.getStringList("categories.lore");
        if (!lore.isEmpty()) {
            List<String> newLore = new ArrayList<>();

            for (String line : lore) {
                if (line.contains("%category_list%")) {
                    for (String category : categories) {
                        String categoryName = Utils.strip(DeluxeAuctions.getInstance().categoriesFile.getString(category + ".name"));

                        String type;
                        if (category.equals(this.playerAuction.getCategory().getName()))
                            type = "selected";
                        else
                            type = "not_selected";

                        String text = categoriesSection.getString("category." + type);
                        if (text == null)
                            continue;

                        newLore.add(Utils.colorize(text
                                .replace("%category_name%", categoryName)));
                    }
                    continue;
                }

                newLore.add(Utils.colorize(line));
            }

            Utils.changeLore(categoriesItem, newLore, null);
        }
        this.gui.setItem(categoriesSection.getInt("slot"), ClickableItem.of(categoriesItem, (event) -> {
            ClickType clickType = event.getClick();

            int number = categories.indexOf(this.playerAuction.getCategory().getName());
            if (clickType.equals(ClickType.RIGHT) || clickType.equals(ClickType.SHIFT_RIGHT)) {
                number--;
                if (number < 0)
                    number = categories.size()-1;
            }
            else {
                number++;
                if (number >= categories.size())
                    number = 0;
            }

            open(categories.get(number), 1);
        }));
    }

    private void loadSorterItem() {
        ItemStack sorterItem = Utils.createItemFromSection(this.section.getConfigurationSection("auction_sorter"), null);
        if (sorterItem == null)
            return;

        List<String> lore = this.section.getStringList("auction_sorter.lore." + this.playerAuction.getSortType().name().toLowerCase(Locale.ENGLISH));
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
            int currentType = !types.isEmpty() && types.contains(this.playerAuction.getSortType().name()) ? types.indexOf(this.playerAuction.getSortType().name()) : 0;

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
            this.playerAuction.setSortType(SortType.valueOf(newType.toUpperCase(Locale.ENGLISH)));

            open(this.playerAuction.getCategory().getName(), Math.min(this.playerAuction.getPage(), this.totalPage));
        }));
    }

    private void loadRaritySorter() {
        ItemStack sorterItem = Utils.createItemFromSection(this.section.getConfigurationSection("rarity_sorter"), null);
        if (sorterItem == null)
            return;

        List<String> lore = this.section.getStringList("rarity_sorter.lore." + this.playerAuction.getRarityType());
        List<String> newLore = new ArrayList<>();
        if (!lore.isEmpty())
            for (String line : lore)
                newLore.add(Utils.colorize(line));

        Utils.changeLore(sorterItem, newLore, null);

        int slot = this.section.getInt("rarity_sorter.slot");
        this.gui.setItem(slot, ClickableItem.of(sorterItem, event -> {
            ClickType clickType = event.getClick();
            Utils.playSound(player, "rarity_item_click");

            List<String> types = this.section.getConfigurationSection("rarity_sorter.types").getKeys(false).stream().toList();
            int currentType = !types.isEmpty() && types.contains(this.playerAuction.getRarityType()) ? types.indexOf(this.playerAuction.getRarityType()) : 0;

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
            this.playerAuction.setRarityType(newType);

            open(this.playerAuction.getCategory().getName(), Math.min(this.playerAuction.getPage(), this.totalPage));
        }));
    }

    private void loadFilterItem() {
        ItemStack filterItem = Utils.createItemFromSection(this.section.getConfigurationSection("auction_filter"), null);
        if (filterItem == null)
            return;

        boolean economyFilterAvailable = isEconomyFilterAvailable();

        List<String> lore = this.section.getStringList("auction_filter.lore." + this.playerAuction.getAuctionType().name().toLowerCase(Locale.ENGLISH));
        List<String> newLore = new ArrayList<>();
        for (String line : lore) {
            // %economy_filter_list% expands to one line per currency (skipped entirely when unavailable)
            if (line.contains("%economy_filter_list%")) {
                if (!economyFilterAvailable)
                    continue;

                for (String option : getEconomyFilterOptions()) {
                    String state = option.equals(this.playerAuction.getEconomyFilter()) ? "selected" : "not_selected";
                    String format = this.section.getString("auction_filter.currency_filter." + state);
                    if (format == null)
                        continue;

                    newLore.add(Utils.colorize(format.replace("%economy_name%", economyFilterName(option))));
                }
                continue;
            }

            // Currency-tagged lines vanish entirely when the sub-filter isn't available
            if (!economyFilterAvailable && line.contains("%economy_filter%"))
                continue;

            newLore.add(Utils.colorize(line.replace("%economy_filter%", economyFilterName(this.playerAuction.getEconomyFilter()))));
        }

        Utils.changeLore(filterItem, newLore, null);
        int slot = this.section.getInt("auction_filter.slot");
        this.gui.setItem(slot, ClickableItem.of(filterItem, event -> {
            ClickType clickType = event.getClick();
            Utils.playSound(player, "filter_item_click");

            // Shift-click cycles the currency sub-filter; reserved only when more than one currency is enabled
            if (economyFilterAvailable && (clickType.equals(ClickType.SHIFT_LEFT) || clickType.equals(ClickType.SHIFT_RIGHT))) {
                List<String> options = getEconomyFilterOptions();
                int index = options.indexOf(this.playerAuction.getEconomyFilter());
                if (index < 0)
                    index = 0;

                if (clickType.equals(ClickType.SHIFT_RIGHT)) {
                    index--;
                    if (index < 0)
                        index = options.size()-1;
                } else {
                    index++;
                    if (index >= options.size())
                        index = 0;
                }

                this.playerAuction.setEconomyFilter(options.get(index));
                open(this.playerAuction.getCategory().getName(), Math.min(this.playerAuction.getPage(), this.totalPage));
                return;
            }

            List<String> types = this.section.getStringList("auction_filter.types");
            int currentType = !types.isEmpty() && types.contains(this.playerAuction.getAuctionType().name()) ? types.indexOf(this.playerAuction.getAuctionType().name()) : 0;

            // backwards (plain right click only; shift is reserved for the currency sub-filter)
            if (!types.isEmpty()) {
                if (clickType.equals(ClickType.RIGHT)) {
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
            this.playerAuction.setAuctionType(AuctionType.valueOf(newType.toUpperCase(Locale.ENGLISH)));

            open(this.playerAuction.getCategory().getName(), Math.min(this.playerAuction.getPage(), this.totalPage));
        }));
    }

    // Currency sub-filter is only meaningful when at least two currencies are enabled
    private boolean isEconomyFilterAvailable() {
        return this.section.getBoolean("auction_filter.currency_filter.enabled", true)
                && DeluxeAuctions.getInstance().economies.size() >= 2;
    }

    // "all" + each enabled economy key, ordered by economy.yml slot for a stable cycle
    private List<String> getEconomyFilterOptions() {
        List<Economy> economies = new ArrayList<>(DeluxeAuctions.getInstance().economies.values());
        economies.sort(Comparator.comparingInt(Economy::getSlot));

        List<String> options = new ArrayList<>(economies.size() + 1);
        options.add("all");
        for (Economy economy : economies)
            options.add(economy.getKey());

        return options;
    }

    private String economyFilterName(String key) {
        if (key == null || key.equals("all"))
            return this.section.getString("auction_filter.currency_filter.all_name", "&aHepsi");

        Economy economy = DeluxeAuctions.getInstance().economies.get(key);
        if (economy == null)
            return key;

        String filterName = economy.getFilterName();
        return (filterName != null && !filterName.isEmpty()) ? filterName : economy.getName();
    }

    private void updateItems() {
        // epoch invalidates timers from previous open() calls, otherwise they stack up on every page switch
        int epoch = this.updaterEpoch;
        Runnable runnable = () -> {
            if (epoch != this.updaterEpoch)
                return;

            loadItems();
        };
        TaskUtils.runTimerAsync(this.player, "auctions", runnable, 20, 20);
    }

    private void loadItems() {
        if (this.slots.isEmpty())
            return;

        HInventory gui = this.gui;
        if (gui == null)
            return;

        List<Auction> auctions = this.currentAuctions;

        int i = 0;
        for (int slot : this.slots) {
            if (slot <= 0)
                continue;

            if (i >= auctions.size()) {
                gui.setItem(slot, null);
                continue;
            }

            Auction auction = auctions.get(i);
            ItemStack itemStack = AuctionHook.getUpdatedAuctionItem(auction);

            gui.setItem(slot, ClickableItem.of(itemStack, (event) -> {
                Utils.playSound(this.player, "auction_item_click");

                if (auction.getAuctionType().equals(AuctionType.BIN))
                    new BinViewMenu(this.player, auction).open("auctions");
                else
                    new NormalViewMenu(this.player, auction).open("auctions");
            }));
            i++;
        }
    }

    private void loadPaginationCategories(ConfigurationSection categoriesSection) {
        List<Integer> slots = categoriesSection.getIntegerList("pagination.slots");
        int nextSlot = categoriesSection.getInt("next_page.slot");
        int previousSlot = categoriesSection.getInt("previous_page.slot");

        String fillType =  categoriesSection.getString("pagination.fill_type", "all_glass");
        if (fillType.endsWith("glass") && !isPackApplied()) {
            this.gui.setItem(previousSlot, ClickableItem.empty(this.playerAuction.getCategory().getGlass()));
            this.gui.setItem(nextSlot, ClickableItem.empty(this.playerAuction.getCategory().getGlass()));

            if (fillType.startsWith("page"))
                slots.forEach(a -> this.gui.setItem(a, ClickableItem.empty(null)));
            else
                slots.forEach(a -> this.gui.setItem(a, ClickableItem.empty(this.playerAuction.getCategory().getGlass())));
        } else {
            slots.forEach(a -> this.gui.setItem(a, ClickableItem.empty(null)));
            this.gui.setItem(previousSlot, ClickableItem.empty(null));
            this.gui.setItem(nextSlot, ClickableItem.empty(null));
        }

        List<Category> categories = new ArrayList<>(CategoryCache.getCategories().values());
        categories.sort(Comparator.comparingInt(Category::getPriority));

        int totalPage = CategoryCache.getTotalPage();
        int currentPage = this.playerAuction.getCategoryPage();

        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%current_page%", String.valueOf(currentPage))
                .addPlaceholder("%total_page%", String.valueOf(totalPage));

        boolean addPageSlots = categoriesSection.getBoolean("pagination.add_page_slots", true);
        if (currentPage > 1) {
            ItemStack previous = Utils.createItemFromSection(categoriesSection.getConfigurationSection("previous_page"), placeholderUtil);
            this.gui.setItem(previousSlot, ClickableItem.of(previous, (event) -> {
                ClickType clickType = event.getClick();
                if (clickType.equals(ClickType.RIGHT) || clickType.equals(ClickType.SHIFT_RIGHT))
                    this.playerAuction.setCategoryPage(1);
                else
                    this.playerAuction.setCategoryPage(currentPage - 1);

                loadPaginationCategories(categoriesSection);
            }));
        } else if (addPageSlots) {
            Category currentCategory = categories.getFirst();
            loadCategory(currentCategory, previousSlot);

            categories.remove(currentCategory);
        }

        int minimum = slots.size();
        if (addPageSlots)
            minimum++;

        int startIndex = 0;
        int endIndex = Math.min(minimum, categories.size());
        if (currentPage > 1) {
            startIndex = minimum + (currentPage - 2) * slots.size();
            if (currentPage == totalPage)
                endIndex = categories.size();
            else
                endIndex = Math.min(startIndex + slots.size(), categories.size());
        }
        if (addPageSlots)
            slots.add(nextSlot);

        int a = 0;
        for (int i = startIndex; i < endIndex; i++) {
            int slot = slots.get(a);
            Category currentCategory = categories.get(i);
            loadCategory(currentCategory, slot);

            a++;
        }

        if (totalPage > currentPage) {
            ItemStack next = Utils.createItemFromSection(categoriesSection.getConfigurationSection("next_page"), placeholderUtil);
            this.gui.setItem(nextSlot, ClickableItem.of(next, (event) -> {
                ClickType clickType = event.getClick();
                if (clickType.equals(ClickType.RIGHT) || clickType.equals(ClickType.SHIFT_RIGHT))
                    this.playerAuction.setCategoryPage(totalPage);
                else
                    this.playerAuction.setCategoryPage(currentPage + 1);

                loadPaginationCategories(categoriesSection);
            }));
        }
    }

    private void loadCategories() {
        ConfigurationSection categoriesSection = this.section.getConfigurationSection("category_list");
        if (categoriesSection == null) {
            for (Category auctionCategory : CategoryCache.getCategories().values())
                loadCategory(auctionCategory, auctionCategory.getSlot());
            return;
        }

        String type = categoriesSection.getString("type", "normal");
        if (type.equalsIgnoreCase("pagination")) {
            loadPaginationCategories(categoriesSection);
            return;
        }

        for (Category auctionCategory : CategoryCache.getCategories().values())
            loadCategory(auctionCategory, auctionCategory.getSlot());
    }

    private void loadCategory(Category auctionCategory, int slot) {
        ItemStack categoryItem = auctionCategory.getItem();
        if (categoryItem == null)
            return;
        categoryItem = categoryItem.clone();

        boolean isCategorySame = auctionCategory.getName().equalsIgnoreCase(this.playerAuction.getCategory().getName());
        boolean hasPermission = Utils.hasPermission(this.player, "category", auctionCategory.getName());

        String type;
        if (hasPermission) {
            if (isCategorySame) {
                if (DeluxeAuctions.getInstance().version >= 21) {
                    ItemMeta meta = categoryItem.getItemMeta();
                    if (meta == null)
                        return;

                    meta.addEnchant(Enchantment.SILK_TOUCH, 1, true);
                    categoryItem.setItemMeta(meta);
                } else
                    categoryItem.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);

                type = "selected";
            } else
                type = "not_selected";
        }
        else
            type = "no_permission";

        List<String> lore = DeluxeAuctions.getInstance().messagesFile.getStringList("lores.category_items." + type);
        List<String> newLore = new ArrayList<>();
        for (String line : lore) {
            if (line.contains("%category_description%")) {
                List<String> description = auctionCategory.getDescription();
                if (!description.isEmpty()) {
                    for (String desc : description)
                        newLore.add(Utils.colorize(desc
                                .replace("%category_item_amount%", String.valueOf(AuctionCache.getFilteredAuctions(this.playerAuction.getAuctionType(), this.playerAuction.getRarityType(), auctionCategory, this.playerAuction.getSearch(), this.playerAuction.getEconomyFilter()).size()))));
                }

                continue;
            }

            newLore.add(Utils.colorize(line));
        }

        Utils.changeLore(categoryItem, newLore, null);
        this.gui.setItem(slot, isCategorySame || !hasPermission ? ClickableItem.empty(categoryItem) : ClickableItem.of(categoryItem, (event) -> {
            Utils.playSound(this.player, "category_item_click");
            open(auctionCategory.getName(), 1);
        }));
    }

    @Override
    public void inputResult(String input) {
        this.playerAuction.setSearch(input == null ? "" : input);
        open("search", 1);
    }

    @Override
    public String getMenuName() {
        return "auctions";
    }
}