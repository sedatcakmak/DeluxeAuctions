package me.sedattr.deluxeauctions.addons;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.auctionsapi.cache.AuctionCache;
import me.sedattr.auctionsapi.cache.CategoryCache;
import me.sedattr.deluxeauctions.managers.AuctionType;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class Placeholders extends PlaceholderExpansion {
    @Override
    public @NotNull String getIdentifier() {
        return "auction";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SedatTR";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        // %auction_owned_auctions%
        if (identifier.contains("owned_auctions")) {
            return String.valueOf(AuctionCache.getOwnedAuctions(player.getUniqueId()).size());
        }

        // %auction_bin_auctions%
        if (identifier.contains("bin_auctions")) {
            return String.valueOf(AuctionCache.countFilteredAuctions(AuctionType.BIN, null, null, null));
        }

        // %auction_normal_auctions%
        if (identifier.startsWith("normal_auctions")) {
            return String.valueOf(AuctionCache.countFilteredAuctions(AuctionType.NORMAL, null, null, null));
        }

        // %auction_bid_auctions%
        if (identifier.contains("bid_auctions")) {
            return String.valueOf(AuctionCache.getBidAuctions(player.getUniqueId()).size());
        }

        /*
        // %auction_balance_formatted%
        if (identifier.contains("balance_formatted")) {
            return DeluxeAuctions.getInstance().numberFormat.format(DeluxeAuctions.getInstance().economyManager.getBalance(player));
        }

        // %auction_balance%
        if (identifier.contains("balance")) {
            return String.valueOf(DeluxeAuctions.getInstance().economyManager.getBalance(player));
        }
         */

        String[] args = identifier.split("[_]", 3);
        if (args.length < 2)
            return "0";

        // %auction_category_auctions_CATEGORY%
        if (identifier.startsWith("category_auctions")) {
            String category = args[args.length - 1];
            if (category == null)
                return "0";

            return String.valueOf(AuctionCache.countFilteredAuctions(AuctionType.ALL, null, CategoryCache.getCategories().get(category), null));
        }

        return "";
    }
}