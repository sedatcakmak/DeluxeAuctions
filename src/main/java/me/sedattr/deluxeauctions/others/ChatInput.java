package me.sedattr.deluxeauctions.others;

import me.sedattr.deluxeauctions.DeluxeAuctions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatInput {
    private final Player player;
    private final ChatListener listener;
    private volatile boolean listening;
    private ChatHandler handler;

    public ChatInput(Player player, ChatHandler handler) {
        this.player = player;
        this.listener = new ChatListener();

        Bukkit.getPluginManager().registerEvents(this.listener, DeluxeAuctions.getInstance());
        this.listening = true;
        this.handler = handler;

        TaskUtils.runLater(() -> {
            if (this.listening) {
                HandlerList.unregisterAll(this.listener);
                this.handler = null;
                Utils.sendMessage(this.player, "input_lines.chat.too_slow");
            }
        }, DeluxeAuctions.getInstance().configFile.getInt("settings.chat_input_time", 10) * 20L);
    }

    private class ChatListener implements Listener {
        @EventHandler
        public void chatListener(AsyncPlayerChatEvent e){
            Player p = e.getPlayer();
            if (!p.equals(ChatInput.this.player))
                return;

            e.setCancelled(true);
            HandlerList.unregisterAll(listener);
            ChatInput.this.listening = false;

            TaskUtils.run(() -> {
                String result = e.getMessage().trim();

                handler.onChat(result);
                handler = null;
            });
        }
    }

    public interface ChatHandler {
        void onChat(String input);
    }
}
