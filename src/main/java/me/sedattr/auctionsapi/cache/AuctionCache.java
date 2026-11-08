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
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        return getFilteredAuctions(type, rarity, category, search, "all");
    }

    // Below this size the fork/join split costs more than the scan itself, and the common pool
    // is shared with the rest of the server.
    private static final int PARALLEL_THRESHOLD = 2_000;

    public static List<Auction> getFilteredAuctions(AuctionType type, String rarity, Category category, String search, String economyFilter) {
        if (auctions.isEmpty())
            return Collections.emptyList();

        // filter(...) into an ordered collection: the old concurrent-set accumulator dropped
        // encounter order, so auctions with equal sort keys reshuffled on every refresh.
        return filteredStream(type, rarity, category, search, economyFilter)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Counts matches without materialising the list -- callers that only need the size were
     * allocating an ArrayList of every matching auction to immediately throw it away.
     */
    public static int countFilteredAuctions(AuctionType type, String rarity, Category category, String search) {
        return countFilteredAuctions(type, rarity, category, search, "all");
    }

    public static int countFilteredAuctions(AuctionType type, String rarity, Category category, String search, String economyFilter) {
        if (auctions.isEmpty())
            return 0;

        return (int) filteredStream(type, rarity, category, search, economyFilter).count();
    }

    private static Stream<Auction> filteredStream(AuctionType type, String rarity, Category category, String search, String economyFilter) {
        boolean economyAll = economyFilter == null || economyFilter.isEmpty() || economyFilter.equalsIgnoreCase("all");

        // Hoisted out of the per-auction body, where it was a YAML lookup plus a colour
        // translation for every auction in the cache on every single filter pass.
        String rarityLine = null;
        if (rarity != null && !rarity.isEmpty()) {
            String configured = DeluxeAuctions.getInstance().menusFile.getString("auctions_menu.rarity_sorter.types." + rarity);
            if (configured != null && !configured.isEmpty())
                rarityLine = Utils.colorize(configured);
        }

        String lowerCaseSearch = (search == null || search.isEmpty()) ? null : search.toLowerCase(Locale.ENGLISH);
        String finalRarityLine = rarityLine;

        Stream<Auction> stream = auctions.size() >= PARALLEL_THRESHOLD
                ? auctions.values().parallelStream()
                : auctions.values().stream();

        return stream.filter(auction -> matchesFilter(auction, type, finalRarityLine, category, lowerCaseSearch, economyAll, economyFilter));
    }

    private static boolean matchesFilter(Auction auction, AuctionType type, String rarityLine, Category category, String lowerCaseSearch, boolean economyAll, String economyFilter) {
        if (!type.equals(AuctionType.ALL) && !auction.getAuctionType().equals(type))
            return false;
        if (auction.isEnded())
            return false;

        if (!economyAll) {
            Economy economy = auction.getEconomy();
            if (economy == null || !economyFilter.equals(economy.getKey()))
                return false;
        }

        ItemStack itemStack = auction.getAuctionItem();
        if (itemStack == null)
            return false;

        if (category != null && !category.isGlobal())
            if (!auction.getAuctionCategory().equals(category.getName()))
                return false;

        ItemMeta meta = itemStack.getItemMeta();
        if (rarityLine != null) {
            List<String> lore = meta == null ? null : meta.getLore();
            return lore != null && lore.contains(rarityLine);
        }

        if (lowerCaseSearch != null) {
            if (meta instanceof EnchantmentStorageMeta) {
                if (EnchantCache.isEnchantmentAdded(((EnchantmentStorageMeta) meta).getStoredEnchants(), lowerCaseSearch))
                    return true;
            } else if (EnchantCache.isEnchantmentAdded(itemStack.getEnchantments(), lowerCaseSearch))
                return true;

            if (meta != null && meta.getDisplayName() != null)
                if (Utils.strip(meta.getDisplayName().toLowerCase(Locale.ENGLISH)).contains(lowerCaseSearch))
                    return true;

            return itemStack.getType().name().replace("_", " ").toLowerCase(Locale.ENGLISH).contains(lowerCaseSearch);
        }

        return true;
    }

    public static List<Auction> getOnGoingAuctions(List<Auction> filteredAuctions, SortType sort, int page, int slot) {
        if (auctions.isEmpty())
            return Collections.emptyList();

        int lower = (page - 1) * slot;
        if (filteredAuctions.isEmpty() || lower >= filteredAuctions.size())
            return Collections.emptyList();

        switch (sort) {
            case LOWEST_PRICE -> filteredAuctions.sort(Comparator.comparing(Auction::getAuctionPrice));
            case HIGHEST_PRICE -> filteredAuctions.sort(Comparator.comparing(Auction::getAuctionPrice).reversed());
            case ALPHABETICAL -> {
                // Decorate-sort-undecorate. The old comparator ran Utils.strip -- a full colour
                // translation pass -- on both operands of every comparison, so an n-element page
                // cost O(n log n) regex passes instead of n.
                Map<Auction, String> names = new IdentityHashMap<>(filteredAuctions.size());
                for (Auction auction : filteredAuctions)
                    names.put(auction, Utils.strip(Utils.getDisplayName(auction.getAuctionItem())));

                filteredAuctions.sort(Comparator.comparing(names::get, String.CASE_INSENSITIVE_ORDER));
            }
            case MOST_BIDS -> filteredAuctions.sort(Comparator.comparingInt((Auction a) -> a.getAuctionBids().getPlayerBids().size()).reversed());
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
