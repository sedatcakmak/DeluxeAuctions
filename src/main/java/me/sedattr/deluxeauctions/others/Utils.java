package me.sedattr.deluxeauctions.others;

import com.google.common.collect.ImmutableMultimap;
import me.sedattr.deluxeauctions.DeluxeAuctions;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    private static String sanitizeForCommand(String input) {
        if (input == null) return "";
        return input.replaceAll("[;&|`$(){}\\[\\]!#]", "")
                    .replaceAll("\\s+", " ")
                    .trim();
    }

    public static boolean hasPermission(CommandSender player, String... strings) {
        if (player.isOp())
            return true;
        if (strings.length < 2)
            return true;

        String type = strings[0];
        String object = strings[1];
        ConfigurationSection section = DeluxeAuctions.getInstance().configFile.getConfigurationSection("permissions." + type);
        if (section == null)
            return true;

        String enabled = section.getString("enabled");
        if (enabled != null && !enabled.isEmpty() && !section.getBoolean("enabled"))
            return !type.equalsIgnoreCase("bypass");

        if (type.equals("item") || type.equals("category")) {
            String permission = section.getString("permission");
            if (permission == null || permission.isEmpty())
                return true;

            permission = permission
                    .replace("%" + type + "_name%", object);

            List<String> list = section.getStringList(type + "_list");
            if (list.contains(object))
                return player.hasPermission(permission);
            else
                return true;
        }

        String permission = section.getString(object);
        if (permission == null || permission.isEmpty())
            return true;

        return player.hasPermission(permission);
    }

    public static String strip(String text) {
        if (text == null || text.isEmpty())
            return "";

        text = Utils.colorize(text);
        return ChatColor.stripColor(text);
    }

    public static void changeName(ItemStack item, String name, PlaceholderUtil placeholderUtil) {
        if (item == null)
            return;
        if (name.isEmpty())
            return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        meta.setDisplayName(colorize(replacePlaceholders(name, placeholderUtil)));

        item.setItemMeta(meta);
    }

    public static void changeLore(ItemStack item, List<String> lore, PlaceholderUtil placeholderUtil) {
        if (item == null)
            return;
        if (lore.isEmpty())
            return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        List<String> newLore = new ArrayList<>();

        for (String line : lore)
            newLore.add(Utils.colorize(replacePlaceholders(line, placeholderUtil)));

        meta.setLore(newLore);
        item.setItemMeta(meta);
    }

    public static String hex(String message) {
        Pattern pattern = Pattern.compile("#[a-fA-F0-9]{6}");

        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String hexCode = message.substring(matcher.start(), matcher.end());
            String replaceSharp = hexCode.replace('#', 'x');

            char[] ch = replaceSharp.toCharArray();
            StringBuilder builder = new StringBuilder();
            for (char c : ch) {
                builder.append("&").append(c);
            }

            message = message.replace(hexCode, builder.toString());
            matcher = pattern.matcher(message);
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String colorize(String s) {
        if (s == null || s.isEmpty())
            return "";

        if (DeluxeAuctions.getInstance().version < 16)
            return ChatColor.translateAlternateColorCodes('&', s);

        return hex(s);
    }

    public static String itemToBase64(ItemStack item) {
        try {
            if (item == null)
                return "";

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    public static ItemStack itemFromBase64(String data) {
        try {
            if (data == null || data.isEmpty())
                return null;

            // Pre-check: Base64 encoded string length * 3/4 gives approximate decoded size
            if (data.length() > 1_398_102) { // ~1MB after Base64 decode
                Logger.sendConsoleMessage("SECURITY: Base64 input too large! (" + data.length() + " chars)", Logger.LogLevel.WARN);
                return null;
            }

            byte[] decoded = Base64Coder.decodeLines(data);
            if (decoded.length > 1_048_576) { // 1MB limit -- DoS prevention
                Logger.sendConsoleMessage("SECURITY: Base64 size limit exceeded! (" + decoded.length + " bytes)", Logger.LogLevel.WARN);
                return null;
            }

            ByteArrayInputStream inputStream = new ByteArrayInputStream(decoded);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            Object object = dataInput.readObject();
            dataInput.close();

            if (!(object instanceof ItemStack item))
                return null;
            if (item.getType() == null || item.getType() == Material.AIR)
                return null;

            return item;
        } catch (Exception e) {
            Logger.sendConsoleMessage("Base64 deserialization error: " + e.getMessage(), Logger.LogLevel.WARN);
            return null;
        }
    }

    public static String replacePlaceholders(String message, PlaceholderUtil placeholderUtil) {
        if (placeholderUtil == null)
            return message;

        Map<String, String> placeholders = placeholderUtil.getPlaceholders();
        if (placeholders != null && !placeholders.isEmpty())
            for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                String key = placeholder.getKey();
                String value = placeholder.getValue();

                message = message
                        .replace(key, value);
            }

        return message;
    }

    public static boolean isLaggy(Player player) {
        ConfigurationSection playerSection = DeluxeAuctions.getInstance().configFile.getConfigurationSection("settings.anti_lag.player");
        if (playerSection != null && playerSection.getBoolean("enabled")) {
            int maximumPing = playerSection.getInt("maximum_ping", 250);
            int playerPing = 0;
            try {
                playerPing = player.getPing();
            } catch (NoSuchMethodError ignored) {
                try {
                    Object entityPlayer = player.getClass().getMethod("getHandle").invoke(playerPing);
                    playerPing = (int) entityPlayer.getClass().getField("ping").get(entityPlayer);
                } catch (Exception ignored1) {
                }
            }

            if (playerPing > maximumPing)
                return true;
        }

        ConfigurationSection serverSection = DeluxeAuctions.getInstance().configFile.getConfigurationSection("settings.anti_lag.server");
        if (serverSection != null && serverSection.getBoolean("enabled")) {
            double tps = ServerTps.getTPS(serverSection.getInt("seconds", 5)*20);
            if (tps <= 0)
                return false;

            double minimumTps = serverSection.getDouble("minimum_tps", 15);
            return tps < minimumTps;
        }

        return false;
    }

    public static boolean isDisabledWorld(String world) {
        ConfigurationSection section = DeluxeAuctions.getInstance().configFile.getConfigurationSection("settings.disabled_worlds");
        if (section == null || !section.getBoolean("enabled"))
            return false;

        List<String> worlds = section.getStringList("world_list");
        if (worlds.isEmpty())
            return false;

        return worlds.contains(world);
    }

    public static void playSound(Player player, String type) {
        if (type == null || type.isEmpty())
            return;

        ConfigurationSection section = DeluxeAuctions.getInstance().configFile.getConfigurationSection("sounds." + type);
        if (section == null)
            return;
        if (!section.getBoolean("enabled"))
            return;

        String name = section.getString("name");
        if (name == null || name.isEmpty())
            return;

        Sound sound = Sound.valueOf(name);
        if (sound == null)
            return;

        player.playSound(player.getLocation(), sound, (float) section.getDouble("volume", 1.0), (float) section.getDouble("pitch", 1.0));
    }

    public static void broadcastMessage(Player player, String type, PlaceholderUtil placeholderUtil) {
        TaskUtils.runAsync(() -> {
            ConfigurationSection section = DeluxeAuctions.getInstance().configFile.getConfigurationSection("broadcast_messages");
            if (section == null || !section.getBoolean("enabled")) {
                if (!type.endsWith("broadcast"))
                    Utils.sendMessage(player, type, placeholderUtil);
                return;
            }

            String sendPermission = section.getString("send_permission", "");
            if (!sendPermission.isEmpty()) {
                if (!player.hasPermission(sendPermission) && !player.isOp()) {
                    Utils.sendMessage(player, type, placeholderUtil);
                    return;
                }
            }

            if (type.endsWith("broadcast") && section.getBoolean("check_if_muted") && DeluxeAuctions.getInstance().muteManager != null)
                if (DeluxeAuctions.getInstance().muteManager.isMuted(player))
                    return;

            for (String command : section.getStringList("commands")) {
                String parsed = command
                        .replace("%player_displayname%", sanitizeForCommand(player.getDisplayName()))
                        .replace("%player_name%", sanitizeForCommand(player.getName()))
                        .replace("%player_uuid%", String.valueOf(player.getUniqueId()));

                TaskUtils.run(() -> {
                    if (parsed.startsWith("[player]"))
                        player.performCommand(parsed.replace("[player]", "").trim());
                    else
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed.replace("[console]", "").trim());
                });
            }

            String message = DeluxeAuctions.getInstance().messagesFile.getString(type, "");
            if (message.isEmpty())
                return;

            String text = section.getString(type + ".text");
            if (text == null || text.isEmpty()) {
                Utils.sendMessage(player, type, placeholderUtil);
                return;
            }

            String hover = section.getString(type + ".hover");
            String clickType = section.getString(type + ".type", "SUGGEST_COMMAND");

            Component base = Component.text(Utils.colorize(Utils.replacePlaceholders(message, placeholderUtil)));

            if (hover != null && !hover.isEmpty()) {
                Component hoverComp = Component.text(Utils.colorize(Utils.replacePlaceholders(hover, placeholderUtil)));
                base = base.hoverEvent(HoverEvent.showText(hoverComp));
            }

            try {
                ClickEvent.Action action = ClickEvent.Action.valueOf(clickType);
                base = base.clickEvent(ClickEvent.clickEvent(action, Utils.replacePlaceholders(text, placeholderUtil)));
            } catch (IllegalArgumentException ignored) {}

            Component finalBase = base;

            String seePermission = section.getString("see_permission", "");

            TaskUtils.run(() -> {
                if (type.contains("broadcast")) {
                    if (!seePermission.isEmpty()) {
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            if (!online.hasPermission(seePermission) && !online.isOp())
                                continue;

                            online.sendMessage(finalBase);
                        }
                    } else {
                        Bukkit.getServer().sendMessage(finalBase);
                    }
                }
                else
                    player.sendMessage(finalBase);
            });
        });
    }

    public static Boolean sendMessage(CommandSender player, String text, PlaceholderUtil placeholderUtil) {
        if (player == null)
            return false;

        List<String> messageList = DeluxeAuctions.getInstance().messagesFile.getStringList(text);
        if (messageList.isEmpty()) {
            String message = DeluxeAuctions.getInstance().messagesFile.getString(text);
            if (message == null) {
                DeluxeAuctions.getInstance().dataHandler.debug(text + " %level_color%message is not found in messages.yml");
                return false;
            }
            if (message.isEmpty())
                return false;

            player.sendMessage(placeholderApi(player, colorize(replacePlaceholders(message, placeholderUtil))));
        } else
            for (String message : messageList)
                player.sendMessage(placeholderApi(player, colorize(replacePlaceholders(message, placeholderUtil))));

        return true;
    }

    public static Boolean sendMessage(CommandSender player, String text) {
        if (player == null)
            return false;

        List<String> messageList = DeluxeAuctions.getInstance().messagesFile.getStringList(text);
        if (messageList.isEmpty()) {
            String message = DeluxeAuctions.getInstance().messagesFile.getString(text);

            if (message == null) {
                DeluxeAuctions.getInstance().dataHandler.debug(text + " %level_color%message is not found in messages.yml");
                return false;
            }
            if (message.isEmpty())
                return false;

            player.sendMessage(placeholderApi(player, colorize(message)));
        } else
            for (String message : messageList)
                player.sendMessage(placeholderApi(player, colorize(message)));

        return true;
    }

    public static String capitalize(String text){
        String c = (text != null) ? text.trim() : "";
        String[] words = c.split(" ");

        StringBuilder result = new StringBuilder();
        for (String w : words)
            result.append(w.length() > 1 ? w.substring(0, 1).toUpperCase(Locale.US) + w.substring(1).toLowerCase(Locale.US) : w).append(" ");

        return result.toString().trim();
    }

    public static List<String> getLore(ItemStack item) {
        if (item == null)
            return Collections.emptyList();

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return Collections.emptyList();

        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty())
            return Collections.emptyList();

        List<String> newLore = new ArrayList<>(lore.size());
        for (String line : lore)
            newLore.add(Utils.colorize(line));

        return newLore;
    }

    public static String placeholderApi(CommandSender player, String message) {
        if (!(player instanceof Player))
            return message;
        if (!DeluxeAuctions.getInstance().placeholderApi)
            return message;

        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders((Player) player, message);
    }

    public static String getDisplayName(ItemStack item) {
        if (item == null)
            return "";

        String itemName;

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName())
            itemName = item.getItemMeta().getDisplayName();
        else
            itemName = org.bukkit.ChatColor.WHITE + capitalize(item.getType().name()
                    .replace("_ITEM", "")
                    .replace("_", " "));

        return itemName;
    }

    public static boolean hasEmptySlot(Player player) {
        int slot = player.getInventory().firstEmpty();
        return slot >= 0;
    }

    public static ItemStack createItemFromSection(ConfigurationSection section, PlaceholderUtil placeholderUtil) {
        if (section == null)
            return null;

        Object object = section.get("item");
        if (object instanceof ItemStack)
            return (ItemStack) object;

        String ecoItems = section.getString("eco_items");
        if (ecoItems != null && !ecoItems.isEmpty() && DeluxeAuctions.getInstance().ecoItemsAddon != null) {
            ItemStack itemStack = DeluxeAuctions.getInstance().ecoItemsAddon.getEcoItem(ecoItems);
            if (itemStack != null)
                return itemStack;
        }

        String materialName = section.getString("material");
        if (materialName == null)
            return null;

        Material material = MaterialHelper.getMaterial(materialName);
        if (material == null)
            return null;

        ItemStack item = null;
        if (material.name().toUpperCase(Locale.ENGLISH).contains("SKULL_ITEM") || material.name().toUpperCase(Locale.ENGLISH).contains("PLAYER_HEAD")) {
            String skin = section.getString("skin");
            if (skin != null && !skin.isEmpty())
                item = new SkullTexture().getSkull(material, section.getString("skin"));
        }

        if (item == null) {
            int data = section.getInt("data", 0);
            if (data != 0 && DeluxeAuctions.getInstance().version < 13)
                item = new ItemStack(material, Math.max(section.getInt("amount"), 1), (short) data);
            else
                item = new ItemStack(material, Math.max(section.getInt("amount"), 1));
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        String name = section.getString("name");
        if (name != null && !section.getBoolean("disable_name"))
            meta.setDisplayName(Utils.colorize(replacePlaceholders(name, placeholderUtil)));

        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            List<String> newLore = new ArrayList<>();

            for (String line : lore)
                newLore.add(Utils.colorize(replacePlaceholders(line, placeholderUtil)));

            meta.setLore(newLore);
        }

        List<String> flags = section.getStringList("flags");
        if (!flags.isEmpty()) {
            if (flags.contains("ALL")) {
                if (DeluxeAuctions.getInstance().version > 13)
                    meta.setAttributeModifiers(ImmutableMultimap.of());

                meta.addItemFlags(ItemFlag.values());
            }
            else
                for (String flag : flags)
                    meta.addItemFlags(ItemFlag.valueOf(flag));
        }

        if (DeluxeAuctions.getInstance().version > 13 && section.getInt("model", 0) != 0)
            meta.setCustomModelData(section.getInt("model"));

        item.setItemMeta(meta);

        List<String> enchants = section.getStringList("enchants");
        if (!enchants.isEmpty())
            for (String enchant : enchants) {
                String[] args = enchant.split("[:]", 2);
                if (args.length < 2)
                    continue;

                Enchantment enchantment = Enchantment.getByName(args[0]);
                if (enchantment == null)
                    continue;

                item.addUnsafeEnchantment(enchantment, Integer.parseInt(args[1]));
            }

        return item;
    }
}
