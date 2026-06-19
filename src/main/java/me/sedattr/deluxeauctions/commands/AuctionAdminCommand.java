package me.sedattr.deluxeauctions.commands;

import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.auctionsapi.AuctionHook;
import me.sedattr.auctionsapi.cache.AuctionCache;
import me.sedattr.auctionsapi.cache.PlayerCache;
import me.sedattr.deluxeauctions.converters.AuctionMasterConverter;
import me.sedattr.deluxeauctions.converters.ZAuctionHouseConverter;
import me.sedattr.deluxeauctions.database.DatabaseManager;
import me.sedattr.deluxeauctions.inventoryapi.inventory.InventoryAPI;
import me.sedattr.deluxeauctions.managers.Auction;
import me.sedattr.deluxeauctions.managers.Category;
import me.sedattr.deluxeauctions.managers.PlayerBid;
import me.sedattr.deluxeauctions.managers.PlayerStats;
import me.sedattr.deluxeauctions.menus.*;
import me.sedattr.deluxeauctions.others.Logger;
import me.sedattr.deluxeauctions.others.PlaceholderUtil;
import me.sedattr.deluxeauctions.others.TaskUtils;
import me.sedattr.deluxeauctions.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AuctionAdminCommand implements CommandExecutor, TabCompleter {
    private final HashMap<String, List<String>> args = new HashMap<>();

    public AuctionAdminCommand() {
        ConfigurationSection section = DeluxeAuctions.getInstance().configFile.getConfigurationSection("commands");
        if (section == null) {
            this.args.put("reload", Collections.singletonList("reload"));
            this.args.put("menu", Arrays.asList("menu", "open"));
            this.args.put("cancel", Collections.singletonList("cancel"));
            this.args.put("lock", Collections.singletonList("lock"));
            this.args.put("convert", Collections.singletonList("convert"));
            this.args.put("transfer", Collections.singletonList("transfer"));
        } else {
            this.args.put("reload", section.getStringList("reload"));
            this.args.put("menu", section.getStringList("menu"));
            this.args.put("cancel", section.getStringList("cancel"));
            this.args.put("lock", section.getStringList("lock"));
            this.args.put("convert", section.getStringList("convert"));

            List<String> transferArgs = section.getStringList("transfer");
            this.args.put("transfer", transferArgs.isEmpty() ? Collections.singletonList("transfer") : transferArgs);
        }
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] args) {
        if (!Utils.hasPermission(commandSender, "admin_commands", "command"))
            return Collections.emptyList();

        ArrayList<String> complete = new ArrayList<>();
        this.args.values().forEach(complete::addAll);

        complete.removeIf(type -> !Utils.hasPermission(commandSender, "admin_commands,", type));

        if (args.length == 1)
            return complete;

        return Collections.emptyList();
    }

    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (!Utils.hasPermission(commandSender, "admin_commands", "command")) {
            Utils.sendMessage(commandSender, "no_permission");
            return false;
        }

        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%command_name%", label);

        if (args.length > 0) {
            if (!DeluxeAuctions.getInstance().loaded) {
                Utils.sendMessage(commandSender, "loading");
                return false;
            }

            String lowerCaseArg = args[0].toLowerCase(Locale.ENGLISH);
            if (this.args.get("cancel").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "admin_commands", "cancel")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                if (args.length < 2) {
                    Utils.sendMessage(commandSender, "admin_cancel_usage", placeholderUtil);
                    return false;
                }

                try {
                    UUID uuid = UUID.fromString(args[1]);
                    Auction auction = AuctionCache.getAuction(uuid);
                    if (auction == null)
                        return false;

                    auction.setAuctionEndTime(ZonedDateTime.now().toInstant().getEpochSecond() - 1000);
                    Utils.sendMessage(commandSender, "admin_cancelled", new PlaceholderUtil()
                            .addPlaceholder("%player_displayname%", auction.getAuctionOwnerDisplayName()));
                    return true;
                } catch (Exception e) {
                    Utils.sendMessage(commandSender, "wrong_auction", null);
                }

                return false;
            }

            if (this.args.get("transfer").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "admin_commands", "transfer")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                if (args.length < 3) {
                    Utils.sendMessage(commandSender, "admin_transfer_usage", placeholderUtil);
                    return false;
                }

                String fromArg = args[1];
                String toArg = args[2];

                // Offline player resolution can block (online-mode lookups) and the data swap
                // touches the database, so the whole operation runs off the main thread.
                TaskUtils.runAsync(() -> {
                    UUID fromUUID = resolveUUID(fromArg);
                    if (fromUUID == null) {
                        Utils.sendMessage(commandSender, "wrong_player", new PlaceholderUtil()
                                .addPlaceholder("%player_name%", fromArg));
                        return;
                    }

                    UUID toUUID = resolveUUID(toArg);
                    if (toUUID == null) {
                        Utils.sendMessage(commandSender, "wrong_player", new PlaceholderUtil()
                                .addPlaceholder("%player_name%", toArg));
                        return;
                    }

                    if (fromUUID.equals(toUUID)) {
                        Utils.sendMessage(commandSender, "admin_transfer_same");
                        return;
                    }

                    String fromName = nameOf(fromUUID, fromArg);
                    String toName = nameOf(toUUID, toArg);

                    int[] counts = transferData(fromUUID, toUUID, toName);

                    Utils.sendMessage(commandSender, "admin_transferred", new PlaceholderUtil()
                            .addPlaceholder("%from%", fromName)
                            .addPlaceholder("%to%", toName)
                            .addPlaceholder("%auction_count%", String.valueOf(counts[0]))
                            .addPlaceholder("%bid_count%", String.valueOf(counts[1])));
                });

                return true;
            }

            if (this.args.get("convert").contains(lowerCaseArg)) {
                if (commandSender instanceof Player) {
                    Utils.sendMessage(commandSender, "only_console");
                    return false;
                }

                if (!commandSender.isOp()) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                if (args.length < 2) {
                    Utils.sendMessage(commandSender, "admin_convert_usage", placeholderUtil);
                    return false;
                }

                if (DeluxeAuctions.getInstance().converting) {
                    Utils.sendMessage(commandSender, "converting");
                    return false;
                }

                long start = System.currentTimeMillis();
                String type = args[1].toLowerCase(Locale.ENGLISH);

                if (type.startsWith("auctionmaster")) {
                    if (!Bukkit.getPluginManager().isPluginEnabled("AuctionMaster")) {
                        Logger.sendConsoleMessage("AuctionMaster is not enabled!", Logger.LogLevel.ERROR);
                        return false;
                    }

                    CompletableFuture<Boolean> status = new AuctionMasterConverter().convertAuctions();
                    status.thenAccept(value -> {
                        if (value)
                            Utils.sendMessage(commandSender, "converted", new PlaceholderUtil()
                                    .addPlaceholder("%convert_type%", "AuctionMaster")
                                    .addPlaceholder("%convert_time%", String.valueOf(System.currentTimeMillis()-start)));
                        else
                            Logger.sendConsoleMessage("There is a problem in AuctionMaster converter!", Logger.LogLevel.ERROR);
                    });

                    return true;
                }

                if (type.startsWith("zauctionhouse")) {
                    if (!Bukkit.getPluginManager().isPluginEnabled("zAuctionHouseV3")) {
                        Logger.sendConsoleMessage("zAuctionHouse is not enabled!", Logger.LogLevel.ERROR);
                        return false;
                    }

                    CompletableFuture<Boolean> status = new ZAuctionHouseConverter().convertAuctions();
                    status.thenAccept(value -> {
                        if (value)
                            Utils.sendMessage(commandSender, "converted", new PlaceholderUtil()
                                    .addPlaceholder("%convert_type%", "zAuctionHouse")
                                    .addPlaceholder("%convert_time%", String.valueOf(System.currentTimeMillis()-start)));
                        else
                            Logger.sendConsoleMessage("There is a problem in zAuctionHouse converter!", Logger.LogLevel.ERROR);
                    });

                    return true;
                }

                Utils.sendMessage(commandSender, "admin_convert_usage", placeholderUtil);
                return true;
            }

            if (this.args.get("lock").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "admin_commands", "lock")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                DeluxeAuctions.getInstance().locked = !DeluxeAuctions.getInstance().locked;
                for (Player player : Bukkit.getOnlinePlayers())
                    if (!player.isOp() && InventoryAPI.hasInventory(player))
                        player.closeInventory();

                Utils.sendMessage(commandSender, DeluxeAuctions.getInstance().locked ? "locked" : "unlocked");
                return true;
            }

            if (this.args.get("reload").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "admin_commands", "reload")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                long start2 = System.currentTimeMillis();
                DeluxeAuctions.getInstance().reload();

                if (DeluxeAuctions.getInstance().multiServerManager != null)
                    DeluxeAuctions.getInstance().multiServerManager.reload();

                Utils.sendMessage(commandSender, "reloaded", new PlaceholderUtil()
                        .addPlaceholder("%reload_time%", String.valueOf(System.currentTimeMillis() - start2)));
                return true;
            }

            if (this.args.get("menu").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "admin_commands", "menu")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                if (args.length < 2) {
                    Utils.sendMessage(commandSender, "admin_menu_usage", placeholderUtil);
                    return false;
                }

                Player b = Bukkit.getPlayerExact(args[1]);
                if (b == null) {
                    Utils.sendMessage(commandSender, "wrong_player", placeholderUtil
                            .addPlaceholder("%player_name%", args[1]));
                    return false;
                }

                if (args.length > 2) {
                    Category category = AuctionHook.getCategory(args[2]);
                    if (category != null) {
                        new AuctionsMenu(b).open(category.getName(), 1);
                        return true;
                    } else {
                        switch (args[2]) {
                            case "manage":
                                new ManageMenu(b).open(1, "command");
                                return true;
                            case "bids":
                                new BidsMenu(b).open(1, "command");
                                return true;
                            case "create":
                                new CreateMenu(b).open("command");
                                return true;
                            case "main":
                                new MainMenu(b).open();
                                return true;
                            case "stats":
                                new StatsMenu(b).open();
                                return true;
                        }
                    }
                }

                AuctionHook.openMainMenu(b);
                return true;
            }
        }

        Utils.sendMessage(commandSender, "admin_usage", placeholderUtil);
        return false;
    }

    private UUID resolveUUID(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(input);
        if (player.hasPlayedBefore() || player.isOnline())
            return player.getUniqueId();

        return null;
    }

    private String nameOf(UUID uuid, String fallback) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : fallback;
    }

    private int[] transferData(UUID fromUUID, UUID toUUID, String toName) {
        int auctionCount = 0;
        int bidCount = 0;

        for (Auction auction : AuctionCache.getAuctions().values()) {
            boolean changed = false;

            // Reassign ownership of the auction itself
            if (auction.getAuctionOwner().equals(fromUUID)) {
                auction.setAuctionOwner(toUUID);
                auction.setAuctionOwnerDisplayName(toName);
                auctionCount++;
                changed = true;
            }

            // Reassign every bid (covers NORMAL bids and BIN purchase records) owned by the source
            List<PlayerBid> bids = auction.getAuctionBids().getPlayerBids();
            boolean bidChanged = false;
            List<PlayerBid> newBids = new ArrayList<>(bids.size());
            for (PlayerBid bid : bids) {
                if (bid.getBidOwner().equals(fromUUID)) {
                    newBids.add(new PlayerBid(bid.getUuid(), toUUID, toName, bid.getBidPrice(), bid.getBidTime(), bid.isCollected()));
                    bidCount++;
                    bidChanged = true;
                } else
                    newBids.add(bid);
            }
            if (bidChanged) {
                auction.getAuctionBids().addPlayerBids(newBids);
                changed = true;
            }

            if (changed) {
                AuctionCache.addUpdatingAuction(auction.getAuctionUUID());
                DeluxeAuctions.getInstance().databaseManager.saveAuction(auction);
            }
        }

        transferStats(fromUUID, toUUID);

        return new int[]{auctionCount, bidCount};
    }

    private void transferStats(UUID fromUUID, UUID toUUID) {
        DatabaseManager db = DeluxeAuctions.getInstance().databaseManager;

        // Source stats: prefer the live cache (online player), otherwise read from disk
        PlayerStats fromStats = PlayerCache.getStats().get(fromUUID);
        if (fromStats == null)
            fromStats = db.loadStatsSync(fromUUID);
        if (fromStats == null)
            return; // source has no stats to merge

        // Target stats: prefer the live cache so an online player's object is updated in place
        PlayerStats toStats = PlayerCache.getStats().get(toUUID);
        if (toStats == null) {
            toStats = db.loadStatsSync(toUUID);
            if (toStats == null)
                toStats = new PlayerStats(toUUID);
        }

        toStats.setWonAuctions(toStats.getWonAuctions() + fromStats.getWonAuctions());
        toStats.setLostAuctions(toStats.getLostAuctions() + fromStats.getLostAuctions());
        toStats.setTotalBids(toStats.getTotalBids() + fromStats.getTotalBids());
        toStats.setHighestBid(fromStats.getHighestBid()); // setter keeps the larger value
        toStats.setSpentMoney(toStats.getSpentMoney() + fromStats.getSpentMoney());
        toStats.setCreatedAuctions(toStats.getCreatedAuctions() + fromStats.getCreatedAuctions());
        toStats.setExpiredAuctions(toStats.getExpiredAuctions() + fromStats.getExpiredAuctions());
        toStats.setSoldAuctions(toStats.getSoldAuctions() + fromStats.getSoldAuctions());
        toStats.setEarnedMoney(toStats.getEarnedMoney() + fromStats.getEarnedMoney());
        toStats.setTotalFees(toStats.getTotalFees() + fromStats.getTotalFees());

        db.saveStats(toStats);
        db.deleteStats(fromUUID);
        PlayerCache.removeStats(fromUUID);
    }
}
