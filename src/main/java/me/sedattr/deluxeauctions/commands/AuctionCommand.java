package me.sedattr.deluxeauctions.commands;

import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.auctionsapi.AuctionHook;
import me.sedattr.auctionsapi.events.ItemPreviewEvent;
import me.sedattr.auctionsapi.cache.AuctionCache;
import me.sedattr.auctionsapi.cache.CategoryCache;
import me.sedattr.auctionsapi.cache.PlayerCache;
import me.sedattr.deluxeauctions.managers.*;
import me.sedattr.deluxeauctions.menus.*;
import me.sedattr.deluxeauctions.others.PlaceholderUtil;
import me.sedattr.deluxeauctions.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.ZonedDateTime;
import java.util.*;

public class AuctionCommand implements CommandExecutor, TabCompleter {
    private final HashMap<Player, Long> commandCooldown = new HashMap<>();

    private final HashMap<String, List<String>> args = new HashMap<>();
    public AuctionCommand() {
        ConfigurationSection section = DeluxeAuctions.getInstance().configFile.getConfigurationSection("commands");
        if (section == null) {
            this.args.put("info", Collections.singletonList("info"));
            this.args.put("menu", Arrays.asList("menu", "open"));
            this.args.put("sell", Collections.singletonList("sell"));
            this.args.put("auctions", Collections.singletonList("auctions"));
            this.args.put("search", Collections.singletonList("search"));
            this.args.put("view", Collections.singletonList("view"));
            this.args.put("manage", Collections.singletonList("manage"));
            this.args.put("bids", Collections.singletonList("bids"));
        } else {
            this.args.put("info", section.getStringList("info"));
            this.args.put("menu", section.getStringList("menu"));
            this.args.put("sell", section.getStringList("sell"));
            this.args.put("auctions", section.getStringList("auctions"));
            this.args.put("search", section.getStringList("search"));
            this.args.put("view", section.getStringList("view"));
            this.args.put("manage", section.getStringList("manage"));
            this.args.put("bids", section.getStringList("bids"));
        }

        Set<String> keys = section.getKeys(false);
        for (String key : keys) {
            if (key.equals("reload") || key.equals("cancel") || key.equals("lock") || key.equals("convert"))
                continue;

            this.args.put(key, section.getStringList(key));
        }
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] args) {
        if (!Utils.hasPermission(commandSender, "player_commands", "command"))
            return Collections.emptyList();

        if (args.length > 0 && this.args.get("view").contains(args[0].toLowerCase(Locale.ENGLISH))) {
            List<String> set = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(a -> set.add(a.getName()));

            return set;
        }

        ArrayList<String> complete = new ArrayList<>();
        this.args.values().forEach(complete::addAll);

        complete.removeIf(type -> !Utils.hasPermission(commandSender, "player_commands", type));

        if (args.length == 1)
            return complete;

        return Collections.emptyList();
    }

    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (!Utils.hasPermission(commandSender, "player_commands", "command")) {
            Utils.sendMessage(commandSender, "no_permission");
            return false;
        }

        if (!(commandSender instanceof Player player)) {
            Utils.sendMessage(commandSender, "not_player");
            return false;
        }

        long cooldown = commandCooldown.getOrDefault(player, 0L);
        if (cooldown > 0) {
            long time = ZonedDateTime.now().toInstant().getEpochSecond() - cooldown;
            if (time < 1.5) {
                Utils.sendMessage(player, "command_cooldown");
                return false;
            }
        }
        commandCooldown.put(player, ZonedDateTime.now().toInstant().getEpochSecond());

        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%command_name%", label);

        if (args.length > 0) {
            if (DeluxeAuctions.getInstance().locked && !player.isOp()) {
                Utils.sendMessage(player, "closed");
                return false;
            }

            if (!DeluxeAuctions.getInstance().loaded) {
                Utils.sendMessage(player, "loading");
                return false;
            }

            if (Utils.isDisabledWorld(player.getWorld().getName())) {
                Utils.sendMessage(player, "disabled_world");
                return false;
            }

            if (Utils.isLaggy(player)) {
                Utils.sendMessage(player, "laggy");
                return false;
            }

            String lowerCaseArg = args[0].toLowerCase(Locale.ENGLISH);

            if (lowerCaseArg.equals("info") || this.args.get("info").contains(lowerCaseArg)) {
                List<String> lines = new ArrayList<>(
                        Arrays.asList("&8[&6DeluxeAuctions&8] &6Plugin Information",
                                "&8- &fDeluxeAuctions &eis made by &fSedatTR&e.",
                                "&8- &eDiscord Support Server: &fdiscord.gg/nchk86TKMT",
                                "&8- &eCurrent Plugin Version: &fv" + DeluxeAuctions.getInstance().getDescription().getVersion()));

                for (String line : lines)
                    player.sendMessage(Utils.colorize(line));

                return true;
            }

            if (this.args.get("bids").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "player_commands", "bids")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                new BidsMenu(player).open(1, "command");
                return true;
            }

            if (this.args.get("menu").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "player_commands", "menu")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                AuctionHook.openMainMenu(player);
                return true;
            }

            if (this.args.get("manage").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "player_commands", "manage")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                new ManageMenu(player).open(1, "command");
                return true;
            }

            if (this.args.get("search").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "player_commands", "search")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                if (args.length < 2) {
                    Utils.sendMessage(commandSender, "search_usage");
                    return false;
                }

                String search = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                PlayerPreferences playerAuction = PlayerCache.getPreferences(player.getUniqueId());
                playerAuction.setSearch(search);

                new AuctionsMenu(player).open("search", 1);
                return true;
            }

            if (this.args.get("auctions").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "player_commands", "auctions")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                String category;
                if (args.length > 1 && CategoryCache.getCategories().containsKey(args[1]))
                    category = args[1];
                else
                    category = PlayerCache.getPlayers().containsKey(player.getUniqueId()) ? PlayerCache.getPreferences(player.getUniqueId()).getCategory().getName() : DeluxeAuctions.getInstance().category;

                new AuctionsMenu(player).open(category, 1);
                return true;
            }

            if (this.args.get("view").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "player_commands", "view")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                if (args.length < 2) {
                    Utils.sendMessage(player, "view_usage", placeholderUtil);
                    return false;
                }

                try {
                    UUID uuid = UUID.fromString(args[1]);
                    Auction auction = AuctionCache.getAuction(uuid);
                    if (auction != null) {
                        if (auction.getAuctionType().equals(AuctionType.BIN))
                            new BinViewMenu(player, auction).open("command");
                        else
                            new NormalViewMenu(player, auction).open("command");
                        return true;
                    }

                    Auction endedAuction = AuctionCache.getEndedAuction(uuid);
                    if (endedAuction != null) {
                        Utils.sendMessage(player, "ended_auction", placeholderUtil);
                        return false;
                    }

                    Player target = Bukkit.getPlayer(uuid);
                    if (target == null) {
                        Utils.sendMessage(player, "view_usage", placeholderUtil);
                        return false;
                    }

                    new ViewAuctionsMenu(player, target).open(1);
                    return true;
                } catch(Exception e) {
                    try {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

                        new ViewAuctionsMenu(player, target).open(1);
                    } catch (Exception ee) {
                        Utils.sendMessage(player, "view_usage", placeholderUtil);
                        return false;
                    }
                }

                return false;
            }

            if (this.args.get("sell").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(player, "player_commands", "sell")) {
                    Utils.sendMessage(player, "no_permission");
                    return false;
                }

                if (args.length < 2) {
                    Utils.sendMessage(player, "sell_usage", placeholderUtil);
                    return false;
                }

                // Check if item is wrong
                int slot = player.getInventory().getHeldItemSlot();
                ItemStack item = slot >= 0 ? player.getInventory().getItem(slot) : null;

                if (item == null || item.getType() == Material.AIR) {
                    Utils.sendMessage(player, "wrong_item", placeholderUtil);
                    return false;
                }

                // Check if item is sellable
                String sellable = AuctionHook.isSellable(player, item);
                if (!sellable.isEmpty()) {
                    Utils.sendMessage(player, sellable);
                    return false;
                }

                // Price Check
                double price;
                double reversedPrice = DeluxeAuctions.getInstance().numberFormat.reverseFormat(args[1]);
                if (reversedPrice > 1)
                    price = reversedPrice;
                else {
                    try {
                        price = Double.parseDouble(args[1]);
                    } catch (Exception e) {
                        Utils.sendMessage(player, "wrong_price", placeholderUtil);
                        return false;
                    }
                }

                if (price <= 0) {
                    Utils.sendMessage(player, "wrong_price", placeholderUtil);
                    return false;
                }

                // Price Limit Check
                double priceLimit = AuctionHook.getPriceLimit(player, "price_limit");
                if (price > priceLimit) {
                    Utils.sendMessage(player, "reached_price_limit", new PlaceholderUtil()
                            .addPlaceholder("%price_limit%", DeluxeAuctions.getInstance().numberFormat.format(priceLimit)));
                    return false;
                }

                // Time Check
                int time = DeluxeAuctions.getInstance().createTime;
                if (args.length > 2) {
                    StringBuilder times = new StringBuilder();
                    for (int i = 2; i < args.length; i++)
                        times.append(args[i]).append(" ");

                    try {
                        time = DeluxeAuctions.getInstance().timeFormat.convertTime(times.toString());
                    } catch (Exception e) {
                        Utils.sendMessage(player, "wrong_duration", placeholderUtil);
                        return false;
                    }
                }

                if (time <= 0) {
                    Utils.sendMessage(player, "wrong_duration", placeholderUtil);
                    return false;
                }

                // Time Limit Check
                int limit = AuctionHook.getLimit(player, "duration_limit");
                if (time > limit) {
                    Utils.sendMessage(player, "reached_duration_limit", new PlaceholderUtil()
                            .addPlaceholder("%duration_limit%", String.valueOf(limit)));
                    return false;
                }

                // Auction Type Check
                String type = args[args.length - 1];
                if (!type.equalsIgnoreCase("bin") && !type.equalsIgnoreCase("normal"))
                    type = DeluxeAuctions.getInstance().configFile.getString("settings.default_type", "bin");

                // 2. Type Check
                if (!type.equalsIgnoreCase("bin") && !type.equalsIgnoreCase("normal")) {
                    Utils.sendMessage(player, "wrong_type", placeholderUtil);
                    return false;
                }

                // Check if auction type is disabled
                if (AuctionHook.isAuctionTypeDisabled(type)) {
                    Utils.sendMessage(player, "disabled_auction_type", new PlaceholderUtil()
                            .addPlaceholder("%auction_type%", args[1].toUpperCase(Locale.ENGLISH)));
                    return false;
                }

                // Preview Item Event
                ItemPreviewEvent event = new ItemPreviewEvent(player, item);
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled())
                    return false;

                PlayerPreferences playerAuction = PlayerCache.getPreferences(player.getUniqueId());
                boolean status = playerAuction.updateCreateItem(player, slot, true);
                if (!status)
                    return false;

                playerAuction.setCreateType(AuctionType.valueOf(type.toUpperCase(Locale.ENGLISH)));
                playerAuction.setCreatePrice(price);
                playerAuction.setCreateTime(time);

                new CreateMenu(player).open("command");
                return true;
            }
        }

        String menuToOpen = DeluxeAuctions.getInstance().configFile.getString("settings.menu_to_open_directly");
        if (menuToOpen != null && !menuToOpen.isEmpty()) {
            if (menuToOpen.equalsIgnoreCase("auctions")) {
                String category = PlayerCache.getPlayers().containsKey(player.getUniqueId()) ? PlayerCache.getPreferences(player.getUniqueId()).getCategory().getName() : DeluxeAuctions.getInstance().category;
                new AuctionsMenu(player).open(category, 1);
            } else {
                AuctionHook.openMainMenu(player);
            }
            return true;
        }

        Utils.sendMessage(player, "player_usage", placeholderUtil);
        return false;
    }
}
