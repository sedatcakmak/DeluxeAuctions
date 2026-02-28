package me.sedattr.deluxeauctions.addons.multiserver;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.sedattr.auctionsapi.cache.AuctionCache;
import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.deluxeauctions.others.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BungeeAddon implements MultiServerManager, PluginMessageListener {
    private final Map<String, Set<String>> updatedAuctions = new ConcurrentHashMap<>();
    private final String channel = "my:deluxeauctions";

    public BungeeAddon() {
        DeluxeAuctions.getInstance().getServer().getMessenger().registerOutgoingPluginChannel(DeluxeAuctions.getInstance(), this.channel);
        DeluxeAuctions.getInstance().getServer().getMessenger().registerIncomingPluginChannel(DeluxeAuctions.getInstance(), this.channel, this);
    }

    public void publish(String text) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(text);

        DeluxeAuctions.getInstance().dataHandler.debug("SENT Bungee Message: &f" + text + " &8(%level_color%Multi Server&8)");
        Iterables.getFirst(Bukkit.getOnlinePlayers(), null).sendPluginMessage(DeluxeAuctions.getInstance(), this.channel, out.toByteArray());
    }

    private boolean publish(UUID uuid, String text) {
        if (isAuctionUpdating(uuid))
            return false;

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(text);

        DeluxeAuctions.getInstance().dataHandler.debug("SENT Bungee Message: &f" + text + " &8(%level_color%Multi Server&8)");
        Iterables.getFirst(Bukkit.getOnlinePlayers(), null).sendPluginMessage(DeluxeAuctions.getInstance(), this.channel, out.toByteArray());
        return true;
    }

    @Override
    public void onPluginMessageReceived(String channel, @NotNull Player player, byte[] bytes) {
        if (!channel.equals(this.channel))
            return;

        ByteArrayDataInput in = ByteStreams.newDataInput(bytes);
        String text = in.readUTF();

        if (text.startsWith("AUCTION")) {
            String uuid = text.split(":", 2)[1];
            AuctionCache.addUpdatingAuction(UUID.fromString(uuid));
        }

        handleMessage(text);
    }

    @Override
    public void reload() {
        publish("RELOAD");
    }

    @Override
    public void updateStats(UUID playerUUID) {
        publish("STATS_UPDATE:" + playerUUID);
    }

    @Override
    public void loadAuction(UUID auctionUUID) {
        publish("AUCTION_LOAD:" + auctionUUID);
    }

    @Override
    public boolean deleteAuction(UUID auctionUUID) {
        return publish(auctionUUID, "AUCTION_DELETE:" + auctionUUID);
    }

    @Override
    public boolean sellerCollectedAuction(UUID auctionUUID) {
        return publish(auctionUUID, "AUCTION_SELLER_COLLECTED:" + auctionUUID);
    }

    @Override
    public boolean buyerCollectedAuction(UUID auctionUUID, UUID playerUUID) {
        return publish(auctionUUID, "AUCTION_BUYER_COLLECTED:" + auctionUUID + ":" + playerUUID);
    }

    @Override
    public boolean playerBoughtAuction(UUID auctionUUID, UUID playerUUID) {
        return publish(auctionUUID, "AUCTION_BOUGHT:" + auctionUUID + ":" + playerUUID);
    }

    @Override
    public boolean playerPlaceBidAuction(UUID auctionUUID, UUID playerUUID, double bidPrice) {
        return publish(auctionUUID, "AUCTION_PLACE_BID:" + auctionUUID + ":" + playerUUID + ":" + bidPrice);
    }

    @Override
    public boolean isAuctionUpdating(UUID uuid) {
        return this.updatedAuctions.containsKey(uuid.toString());
    }

    @Override
    public void removeUpdatingAuction(String uuid, String text) {
        Set<String> auctions = this.updatedAuctions.get(uuid);
        if (auctions == null || auctions.isEmpty())
            this.updatedAuctions.remove(uuid);
        else {
            auctions.remove(text);

            if (auctions.isEmpty())
                this.updatedAuctions.remove(uuid);
        }
    }
}
