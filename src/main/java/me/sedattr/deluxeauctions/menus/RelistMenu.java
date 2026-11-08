package me.sedattr.deluxeauctions.menus;

import me.sedattr.auctionsapi.AuctionHook;
import me.sedattr.auctionsapi.cache.AuctionCache;
import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.deluxeauctions.inventoryapi.HInventory;
import me.sedattr.deluxeauctions.inventoryapi.inventory.InventoryAPI;
import me.sedattr.deluxeauctions.inventoryapi.item.ClickableItem;
import me.sedattr.deluxeauctions.managers.Auction;
import me.sedattr.deluxeauctions.managers.AuctionType;
import me.sedattr.deluxeauctions.managers.Economy;
import me.sedattr.deluxeauctions.others.PlaceholderUtil;
import me.sedattr.deluxeauctions.others.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Preview + confirm screen for putting every expired, unsold auction of a player back on sale
 * at its previous price and duration. Each listing shows what it will be relisted for, and can be
 * clicked to leave it out; the confirm button sums everything up before anything is charged.
 */
public class RelistMenu {
    public static final String SECTION = "relist_auctions";

    private final Player player;
    private final ConfigurationSection section;
    private final List<Auction> auctions;
    private final Set<UUID> excluded = new HashSet<>();

    private HInventory gui;
    private int page;
    private String back;
    private boolean processing = false;

    public RelistMenu(Player player) {
        this.player = player;
        this.section = DeluxeAuctions.getInstance().menusFile.getConfigurationSection(SECTION);
        this.auctions = getRelistable(player.getUniqueId());
    }

    public static boolean isEnabled() {
        return DeluxeAuctions.getInstance().menusFile.isConfigurationSection(SECTION);
    }

    /** Expired, unsold auctions of the player, most recently expired first. */
    public static List<Auction> getRelistable(UUID owner) {
        List<Auction> list = new ArrayList<>();
        for (Auction auction : AuctionCache.getOwnedAuctions(owner))
            if (auction.isExpiredUnsold())
                list.add(auction);

        list.sort(Comparator.comparingLong(Auction::getAuctionEndTime).reversed());
        return list;
    }

    /** The auction's own duration, or the default duration for rows that predate it; capped by the player's limit. */
    public static long getRelistDuration(Player player, Auction auction) {
        long duration = auction.getAuctionDuration() > 0 ? auction.getAuctionDuration() : DeluxeAuctions.getInstance().createTime;

        int limit = AuctionHook.getLimit(player, "duration_limit");
        if (limit > 0)
            duration = Math.min(duration, limit);

        return Math.max(duration, 1);
    }

    /** Same fee a fresh listing with this price, type, economy and duration would pay (see CreateMenu). */
    public static double getRelistFee(Auction auction, long duration) {
        Economy economy = auction.getEconomy();
        double price = auction.getAuctionPrice();
        double fee = 0.0;

        double priceFeePercent = AuctionHook.calculatePriceFeePercent(price, auction.getAuctionType().equals(AuctionType.BIN) ? "bin" : "normal", economy);
        if (priceFeePercent > 0.0)
            fee += AuctionHook.roundFee(price / 100 * priceFeePercent, economy);

        double durationFee = AuctionHook.calculateDurationFee(duration, economy, price);
        if (durationFee > 0.0)
            fee += durationFee;

        return fee;
    }

    private static String formatMoney(Economy economy, double amount) {
        return economy.getText().replace("%price%", DeluxeAuctions.getInstance().numberFormat.format(amount));
    }

    private String typeName(Auction auction) {
        String key = auction.getAuctionType().equals(AuctionType.BIN) ? "bin" : "normal";
        return this.section.getString("types." + key, auction.getAuctionType().name());
    }

    private PlaceholderUtil auctionPlaceholders(Auction auction) {
        long duration = getRelistDuration(this.player, auction);
        return new PlaceholderUtil()
                .addPlaceholder("%item_displayname%", Utils.getDisplayName(auction.getAuctionItem()))
                .addPlaceholder("%item_name%", Utils.strip(Utils.getDisplayName(auction.getAuctionItem())))
                .addPlaceholder("%item_amount%", String.valueOf(auction.getAuctionItem().getAmount()))
                .addPlaceholder("%auction_price%", formatMoney(auction.getEconomy(), auction.getAuctionPrice()))
                .addPlaceholder("%auction_time%", DeluxeAuctions.getInstance().timeFormat.formatTime(duration, "other_times"))
                .addPlaceholder("%auction_fee%", formatMoney(auction.getEconomy(), getRelistFee(auction, duration)))
                .addPlaceholder("%auction_type%", typeName(auction));
    }

    private List<Auction> getSelected() {
        List<Auction> selected = new ArrayList<>();
        for (Auction auction : this.auctions)
            if (!this.excluded.contains(auction.getAuctionUUID()))
                selected.add(auction);

        return selected;
    }

    private int getTotalPage() {
        int size = Math.max(this.section.getIntegerList("slots").size(), 1);
        return Math.max(1, (this.auctions.size() + size - 1) / size);
    }

    public void open(int page, String back) {
        if (this.section == null)
            return;

        if (this.auctions.isEmpty()) {
            Utils.sendMessage(this.player, "relist_nothing");
            new ManageMenu(this.player).open(1, back);
            return;
        }

        this.page = Math.max(1, Math.min(page, getTotalPage()));
        this.back = back;

        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%current_page%", String.valueOf(this.page))
                .addPlaceholder("%total_page%", String.valueOf(getTotalPage()));

        this.gui = DeluxeAuctions.getInstance().menuHandler.createInventory(this.player, this.section, SECTION, placeholderUtil);

        int goBackSlot = this.section.getInt("back");
        ItemStack goBackItem = DeluxeAuctions.getInstance().normalItems.get("go_back");
        if (goBackSlot > 0 && goBackItem != null)
            this.gui.setItem(goBackSlot, ClickableItem.of(goBackItem, (event) -> new ManageMenu(this.player).open(1, this.back)));

        loadItems();
        loadConfirm();
        loadPagination(placeholderUtil);

        this.gui.open(this.player);
    }

    private void loadPagination(PlaceholderUtil placeholderUtil) {
        if (!this.section.getBoolean("pagination"))
            return;

        int totalPage = getTotalPage();
        if (totalPage > this.page) {
            ItemStack nextPage = Utils.createItemFromSection(this.section.getConfigurationSection("next_page"), placeholderUtil);
            if (nextPage != null)
                this.gui.setItem(this.section.getInt("next_page.slot"), ClickableItem.of(nextPage, (event) -> {
                    ClickType clickType = event.getClick();
                    open(clickType.equals(ClickType.RIGHT) || clickType.equals(ClickType.SHIFT_RIGHT) ? totalPage : this.page + 1, this.back);
                }));
        }

        if (this.page > 1) {
            ItemStack previousPage = Utils.createItemFromSection(this.section.getConfigurationSection("previous_page"), placeholderUtil);
            if (previousPage != null)
                this.gui.setItem(this.section.getInt("previous_page.slot"), ClickableItem.of(previousPage, (event) -> {
                    ClickType clickType = event.getClick();
                    open(clickType.equals(ClickType.RIGHT) || clickType.equals(ClickType.SHIFT_RIGHT) ? 1 : this.page - 1, this.back);
                }));
        }
    }

    private void loadItems() {
        List<Integer> slots = this.section.getIntegerList("slots");
        if (slots.isEmpty())
            return;

        int lower = (this.page - 1) * slots.size();
        int upper = Math.min(this.page * slots.size(), this.auctions.size());

        for (int i = lower; i < upper; i++) {
            Auction auction = this.auctions.get(i);
            int slot = slots.get(i - lower);

            boolean isExcluded = this.excluded.contains(auction.getAuctionUUID());
            ItemStack item = auction.getAuctionItem().clone();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PlaceholderUtil placeholderUtil = auctionPlaceholders(auction);
                List<String> lore = new ArrayList<>();
                List<String> itemLore = meta.getLore();
                if (itemLore != null)
                    lore.addAll(itemLore);

                for (String line : this.section.getStringList("listing_lore." + (isExcluded ? "excluded" : "included")))
                    lore.add(Utils.colorize(Utils.replacePlaceholders(line, placeholderUtil)));

                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            this.gui.setItem(slot, ClickableItem.of(item, (event) -> {
                if (!this.excluded.remove(auction.getAuctionUUID()))
                    this.excluded.add(auction.getAuctionUUID());

                Utils.playSound(this.player, "sorter_item_click");
                loadItems();
                loadConfirm();
            }));
        }
    }

    private void loadConfirm() {
        List<Auction> selected = getSelected();

        ConfigurationSection confirmSection = this.section.getConfigurationSection(selected.isEmpty() ? "confirm.without_selected" : "confirm.with_selected");
        if (confirmSection == null)
            return;

        ItemStack itemStack = Utils.createItemFromSection(confirmSection, null);
        if (itemStack == null)
            return;

        if (selected.isEmpty()) {
            this.gui.setItem(confirmSection.getInt("slot"), ClickableItem.empty(itemStack));
            return;
        }

        // Total fee per currency, in economy.yml order so the lines don't reshuffle between clicks.
        Map<String, Double> fees = new LinkedHashMap<>();
        Map<String, Economy> economies = new HashMap<>();
        for (Auction auction : selected) {
            Economy economy = auction.getEconomy();
            fees.merge(economy.getKey(), getRelistFee(auction, getRelistDuration(this.player, auction)), Double::sum);
            economies.put(economy.getKey(), economy);
        }

        int maxLines = this.section.getInt("confirm.max_item_lines", 8);
        String itemLine = this.section.getString("confirm.item_line", "&8• &f%item_displayname% &8| &6%auction_price% &8| &e%auction_time%");
        String moreLine = this.section.getString("confirm.more_line", "&8...ve &7%amount% &8eşya daha");
        String feeLine = this.section.getString("confirm.fee_line", "&8• &6%fee%");
        String freeLine = this.section.getString("confirm.free_line", "&8• &aÜcretsiz");

        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%relist_amount%", String.valueOf(selected.size()));

        List<String> lore = new ArrayList<>();
        for (String line : confirmSection.getStringList("lore")) {
            if (line.contains("%relist_items%")) {
                int shown = Math.min(selected.size(), Math.max(maxLines, 0));
                for (int i = 0; i < shown; i++)
                    lore.add(Utils.replacePlaceholders(itemLine, auctionPlaceholders(selected.get(i))));
                if (selected.size() > shown)
                    lore.add(moreLine.replace("%amount%", String.valueOf(selected.size() - shown)));
                continue;
            }

            if (line.contains("%relist_fees%")) {
                boolean any = false;
                for (Map.Entry<String, Double> entry : fees.entrySet()) {
                    if (entry.getValue() <= 0.0)
                        continue;
                    lore.add(feeLine.replace("%fee%", formatMoney(economies.get(entry.getKey()), entry.getValue())));
                    any = true;
                }
                if (!any)
                    lore.add(freeLine);
                continue;
            }

            lore.add(line);
        }
        Utils.changeLore(itemStack, lore, placeholderUtil);

        this.gui.setItem(confirmSection.getInt("slot"), ClickableItem.of(itemStack, (event) -> confirm()));
    }

    private void confirm() {
        if (this.processing)
            return;
        this.processing = true;

        // Re-validate against the live cache: an item may have been collected from another
        // menu or server since this screen was opened.
        List<Auction> selected = new ArrayList<>();
        for (Auction auction : getSelected())
            if (AuctionCache.getAuction(auction.getAuctionUUID()) == auction && auction.isExpiredUnsold())
                selected.add(auction);

        if (selected.isEmpty()) {
            Utils.sendMessage(this.player, "relist_nothing");
            this.player.closeInventory();
            return;
        }

        // Check every currency up front so a short balance never leaves half the items relisted.
        Map<String, Double> fees = new HashMap<>();
        Map<String, Economy> economies = new HashMap<>();
        for (Auction auction : selected) {
            fees.merge(auction.getEconomy().getKey(), getRelistFee(auction, getRelistDuration(this.player, auction)), Double::sum);
            economies.put(auction.getEconomy().getKey(), auction.getEconomy());
        }
        for (Map.Entry<String, Double> entry : fees.entrySet()) {
            Economy economy = economies.get(entry.getKey());
            double balance = economy.getManager().getBalance(this.player);
            if (balance < entry.getValue()) {
                Utils.playSound(this.player, "not_enough_money");
                Utils.sendMessage(this.player, "relist_not_enough_money", new PlaceholderUtil()
                        .addPlaceholder("%required_money%", formatMoney(economy, entry.getValue() - balance)));
                this.processing = false;
                return;
            }
        }

        int success = 0;
        int failed = 0;
        boolean aborted = false;
        String firstError = null;
        for (Auction auction : selected) {
            long duration = getRelistDuration(this.player, auction);
            String result = auction.relist(this.player, duration, getRelistFee(auction, duration));
            if (result.isEmpty()) {
                success++;
                continue;
            }

            failed++;
            if (firstError == null)
                firstError = result;

            // A create listener that refuses and closes the menu (e.g. "go to the auction NPC")
            // would refuse every remaining item too and send its message once per item.
            if (result.equals("relist_failed") && InventoryAPI.getInventory(this.player) != this.gui) {
                aborted = true;
                break;
            }
        }

        if (success > 0)
            Utils.sendMessage(this.player, "relisted", new PlaceholderUtil()
                    .addPlaceholder("%amount%", String.valueOf(success)));
        if (failed > 0) {
            Utils.sendMessage(this.player, "relist_some_failed", new PlaceholderUtil()
                    .addPlaceholder("%amount%", String.valueOf(failed)));
            // The balance message expects %required_money%, which only the up-front check knows.
            if (firstError != null && !firstError.equals("relist_not_enough_money"))
                Utils.sendMessage(this.player, firstError);
        }

        if (!aborted)
            new ManageMenu(this.player).open(1, this.back);
    }
}
