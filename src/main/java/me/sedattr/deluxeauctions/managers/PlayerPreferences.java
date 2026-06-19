package me.sedattr.deluxeauctions.managers;

import lombok.Getter;
import lombok.Setter;
import me.sedattr.auctionsapi.events.AuctionPreCollectAllEvent;
import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.auctionsapi.cache.AuctionCache;
import me.sedattr.auctionsapi.cache.CategoryCache;
import me.sedattr.auctionsapi.cache.PlayerCache;
import me.sedattr.deluxeauctions.menus.BidsMenu;
import me.sedattr.deluxeauctions.others.PlaceholderUtil;
import me.sedattr.deluxeauctions.others.TaskUtils;
import me.sedattr.deluxeauctions.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class PlayerPreferences {
    private final UUID player;

    // for auctions menu
    private AuctionType auctionType = DeluxeAuctions.getInstance().auctionType;
    private SortType sortType = DeluxeAuctions.getInstance().sortType;
    private String rarityType = DeluxeAuctions.getInstance().rarityType;
    private String economyFilter = "all";
    private String search = "";
    private Category category = CategoryCache.getCategories().get(DeluxeAuctions.getInstance().category);
    private int page = 1;
    private int categoryPage = 1;
    private final AtomicBoolean clicked = new AtomicBoolean(false);

    // for create menu
    private AuctionType createType = DeluxeAuctions.getInstance().createType;
    private double createPrice = DeluxeAuctions.getInstance().createPrice;
    private long createTime = DeluxeAuctions.getInstance().createTime;
    private Economy createEconomy = DeluxeAuctions.getInstance().createEconomy;

    public PlayerPreferences(UUID player) {
        this.player = player;
    }

    public boolean updateCreateItem(Player player, int slot, boolean giveOldItem) {
        if (!this.clicked.compareAndSet(false, true))
            return false;

        if (giveOldItem) {
            ItemStack oldItem = PlayerCache.getItem(this.player);
            PlayerCache.setItem(this.player, null);

            if (oldItem != null) {
                ItemStack[] backup = new ItemStack[36];
                for (int i = 0; i < 36; i++) {
                    ItemStack item = player.getInventory().getItem(i);
                    backup[i] = item != null ? item.clone() : null;
                }
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(oldItem);
                if (!leftover.isEmpty()) {
                    for (int i = 0; i < 36; i++) player.getInventory().setItem(i, backup[i]);
                    Utils.sendMessage(player, "no_empty_slot");
                    PlayerCache.setItem(this.player, oldItem);
                    TaskUtils.runLater(() -> this.clicked.set(false), 1);
                    return false;
                }
                DeluxeAuctions.getInstance().databaseManager.saveItem(this.player, null);
            }
        }

        if (slot >= 0) {
            ItemStack slotItem = player.getInventory().getItem(slot);
            if (slotItem != null && slotItem.getType() != Material.AIR) {
                ItemStack clone = slotItem.clone();
                player.getInventory().setItem(slot, null);
                TaskUtils.run(player::updateInventory);

                PlayerCache.setItem(this.player, clone);
                DeluxeAuctions.getInstance().databaseManager.saveItem(this.player, clone);
            } else {
                PlayerCache.setItem(this.player, null);
                DeluxeAuctions.getInstance().databaseManager.saveItem(this.player, null);
            }
        } else {
            PlayerCache.setItem(this.player, null);
            DeluxeAuctions.getInstance().databaseManager.saveItem(this.player, null);
        }

        TaskUtils.runLater(() -> this.clicked.set(false), 1);
        return true;
    }

    public void collectAuctions(Player player) {
        List<Auction> auctions = new ArrayList<>(AuctionCache.getOwnedAuctions(this.player));

        AuctionPreCollectAllEvent event = new AuctionPreCollectAllEvent(player, auctions, true);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return;

        TaskUtils.runAsync(() -> {
            double money = 0.0;
            int item = 0;

            for (Auction auction : auctions) {
                if (auction == null)
                    continue;

                String result = auction.sellerCollect(player, true);
                if (result.isEmpty())
                    continue;

                PlayerBid playerBid = auction.getAuctionBids().getHighestBid();
                if (playerBid == null)
                    item++;
                else
                    money+=playerBid.getBidPrice();
            }

            DeluxeAuctions.getInstance().dataHandler.writeToLog("[SELLER COLLECTED ALL AUCTIONS] " + player.getName() + " (" + player.getUniqueId() + ") collected " + money + " COINS and " + item + " ITEMS from auction!");
            if (money > 0.0)
                Utils.sendMessage(player, "seller_collected_moneys", new PlaceholderUtil()
                        .addPlaceholder("%total_money_amount%", DeluxeAuctions.getInstance().numberFormat.format(money)));

            if (item > 0)
                Utils.sendMessage(player, "seller_collected_items", new PlaceholderUtil()
                        .addPlaceholder("%total_item_amount%", String.valueOf(item)));
        });
    }

    public void collectBids(Player player, String back) {
        List<Auction> auctions = new ArrayList<>(AuctionCache.getBidAuctions(this.player));

        AuctionPreCollectAllEvent event = new AuctionPreCollectAllEvent(player, auctions, false);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return;

        TaskUtils.runAsync(() -> {
            double money = 0.0;
            int item = 0;

            for (Auction auction : auctions) {
                if (auction == null)
                    continue;

                String result = auction.buyerCollect(player, true);
                if (result.isEmpty())
                    continue;

                PlayerBid playerBid = auction.getAuctionBids().getPlayerBid(player.getUniqueId());
                if (auction.getAuctionBids().getHighestBid() == playerBid)
                    item++;
                else
                    money+=playerBid.getBidPrice();
            }

            DeluxeAuctions.getInstance().dataHandler.writeToLog("[BUYER COLLECTED ALL BIDS] " + player.getName() + " (" + player.getUniqueId() + ") collected " + money + " COINS and " + item + " ITEMS from auction!");
            if (money > 0.0)
                Utils.sendMessage(player, "buyer_collected_moneys", new PlaceholderUtil()
                        .addPlaceholder("%total_money_amount%", DeluxeAuctions.getInstance().numberFormat.format(money)));

            if (item > 0)
                Utils.sendMessage(player, "buyer_collected_items", new PlaceholderUtil()
                        .addPlaceholder("%total_item_amount%", String.valueOf(item)));


            new BidsMenu(player).open(1, back);
        });
    }
}
