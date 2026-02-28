package me.sedattr.auctionsapi.cache;

import lombok.Getter;
import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.deluxeauctions.managers.*;
import me.sedattr.deluxeauctions.others.Utils;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionCache {
    @Getter private static final ConcurrentHashMap<UUID, Auction> auctions = new ConcurrentHashMap<>();
    private static final Set<UUID> updatedAuctions = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, Auction> endedAuctions = new ConcurrentHashMap<>();

    public static boolean isAuctionUpdating(UUID auctionUUID) {
        return updatedAuctions.contains(auctionUUID);
    }

    public static void addUpdatingAuction(UUID auctionUUID) {
        updatedAuctions.add(auctionUUID);
    }

    public static void removeUpdatingAuction(UUID auctionUUID) {
        updatedAuctions.remove(auctionUUID);
    }

    public static Auction getEndedAuction(UUID uuid) {
        return auctions.get(uuid);
    }

    public static void addEndedAuction(Auction auction) {
        if (auction == null)
            return;

        endedAuctions.put(auction.getAuctionUUID(), auction);
    }

    public static void removeEndedAuction(UUID uuid) {
        if (!endedAuctions.containsKey(uuid))
            return;

        endedAuctions.remove(uuid);
    }

    public static Auction getAuction(UUID uuid) {
        return auctions.get(uuid);
    }

    public static void addAuction(Auction auction) {
        if (auction == null)
            return;

        auctions.put(auction.getAuctionUUID(), auction);
    }

    public static void removeAuction(UUID uuid) {
        if (!auctions.containsKey(uuid))
            return;

        auctions.remove(uuid);
    }

    public static List<Auction> getOwnedAuctions(UUID uuid) {
        if (auctions.isEmpty())
            return Collections.emptyList();

        return auctions.values().stream().filter(auction -> !auction.isSellerClaimed() && auction.getAuctionOwner().equals(uuid)).toList();
    }

    public static List<Auction> getBidAuctions(UUID uuid) {
        if (auctions.isEmpty())
            return Collections.emptyList();

        return auctions.values().stream().filter(auction -> {
            PlayerBid playerBid = auction.getAuctionBids().getPlayerBid(uuid);
            if (playerBid == null)
                return false;

            return !playerBid.isCollected();
        }).toList();
    }

    public static List<Auction> getFilteredAuctions(AuctionType type, String rarity, Category category, String search) {
        if (auctions.isEmpty())
            return Collections.emptyList();

        ConcurrentHashMap.KeySetView<Auction, Boolean> result = ConcurrentHashMap.newKeySet();

        auctions.values().parallelStream().forEach(auction -> {
            if (!type.equals(AuctionType.ALL) && !auction.getAuctionType().equals(type))
                return;
            if (auction.isEnded())
                return;
            ItemStack itemStack = auction.getAuctionItem();
            if (itemStack == null)
                return;
            if (category != null && !category.isGlobal())
                if (!auction.getAuctionCategory().equals(category.getName()))
                    return;

            ItemMeta meta = itemStack.getItemMeta();
            if (rarity != null && !rarity.isEmpty()) {
                String line = DeluxeAuctions.getInstance().menusFile.getString("auctions_menu.rarity_sorter.types." + rarity);
                if (line != null && !line.isEmpty()) {
                    line = Utils.colorize(line);

                    List<String> lore = meta.getLore();
                    if (lore == null || lore.isEmpty())
                        return;

                    if (lore.contains(line))
                        result.add(auction);

                    return;
                }
            }

            if (search != null && !search.isEmpty()) {
                String lowerCaseSearch = search.toLowerCase(Locale.ENGLISH);

                if (meta instanceof EnchantmentStorageMeta) {
                    if (EnchantCache.isEnchantmentAdded(((EnchantmentStorageMeta) meta).getStoredEnchants(), lowerCaseSearch)) {
                        result.add(auction);
                        return;
                    }
                } else {
                    if (EnchantCache.isEnchantmentAdded(itemStack.getEnchantments(), lowerCaseSearch)) {
                        result.add(auction);
                        return;
                    }
                }

                if (meta != null) {
                    if (meta.getDisplayName() != null) {
                        if (Utils.strip(meta.getDisplayName().toLowerCase(Locale.ENGLISH)).contains(lowerCaseSearch)) {
                            result.add(auction);
                            return;
                        }
                    }

                    /*
                    if (DeluxeAuctions.getInstance().version >= 21) {
                        String itemName = meta.getItemName();
                        if (itemName != null && !itemName.isEmpty()) {
                            if (Utils.strip(itemName.toLowerCase()).contains(lowerCaseSearch)) {
                                result.add(auction);
                                return;
                            }
                        }
                    }
                    */
                }

                if (itemStack.getType().name().replace("_", " ").toLowerCase(Locale.ENGLISH).contains(lowerCaseSearch))
                    result.add(auction);

                return;
            } else if (category != null && category.isGlobal()) {
                result.add(auction);
                return;
            }

            result.add(auction);
        });

        return new ArrayList<>(result);
    }

    public static List<Auction> getOnGoingAuctions(List<Auction> filteredAuctions, SortType sort, int page, int slot) {
        if (auctions.isEmpty())
            return Collections.emptyList();

        int lower = (page - 1) * slot;
        if (filteredAuctions.isEmpty() || lower >= filteredAuctions.size())
            return Collections.emptyList();

        switch (sort) {
            case LOWEST_PRICE -> filteredAuctions.sort(Comparator.comparing(Auction::getAuctionPrice));
            case HIGHEST_PRICE -> {
                filteredAuctions.sort(Comparator.comparing(Auction::getAuctionPrice));
                Collections.reverse(filteredAuctions);
            }
            case ALPHABETICAL -> filteredAuctions.sort((a, b) -> Utils.strip(Utils.getDisplayName(a.getAuctionItem())).compareToIgnoreCase(Utils.strip(Utils.getDisplayName(b.getAuctionItem()))));
            case MOST_BIDS -> {
                filteredAuctions.sort(Comparator.comparingInt(a -> a.getAuctionBids().getPlayerBids().size()));
                Collections.reverse(filteredAuctions);
            }
            case ENDING_SOON -> filteredAuctions.sort(Comparator.comparing(Auction::getAuctionEndTime));
            case RANDOM -> Collections.shuffle(filteredAuctions);
        }

        int upper = Math.min(page * slot, filteredAuctions.size());
        List<Auction> newAuctions = new ArrayList<>(upper - lower);
        for (int i = lower; i < upper; i++) {
            newAuctions.add(filteredAuctions.get(i));
        }

        return newAuctions;
    }
}
