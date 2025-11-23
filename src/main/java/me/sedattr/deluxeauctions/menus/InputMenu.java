package me.sedattr.deluxeauctions.menus;

import de.rapha149.signgui.SignGUI;
import me.sedattr.auctionsapi.events.InputOpenEvent;
import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.deluxeauctions.others.ChatInput;
import me.sedattr.deluxeauctions.others.TaskUtils;
import me.sedattr.deluxeauctions.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.wesjd.anvilgui.AnvilGUI;

import java.util.Collections;
import java.util.List;

public class InputMenu {
    private String type;

    public InputMenu() {
        this.type = DeluxeAuctions.getInstance().configFile.getString("settings.input_type");
        if (this.type == null)
            this.type = "sign";
    }

    public void open(Player player, MenuManager menuManager) {
        InputOpenEvent event = new InputOpenEvent(player, menuManager.getMenuName());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return;

        if (this.type.equalsIgnoreCase("sign"))
            signInput(player, menuManager);
        else if (this.type.equalsIgnoreCase("anvil"))
            anvilInput(player, menuManager);
        else
            chatInput(player, menuManager);
    }

    private void signInput(Player player, MenuManager menuManager) {
        String textType = menuManager.getClass().equals(AuctionsMenu.class) ? "text" : "number";

        List<String> lines = DeluxeAuctions.getInstance().messagesFile.getStringList("input_lines.sign." + textType);
        if (!lines.isEmpty() && lines.size() > 3) {
            try {
                SignGUI gui = SignGUI.builder()
                        .setLines(lines.get(0), lines.get(1), lines.get(2), lines.get(3))
                        .callHandlerSynchronously(DeluxeAuctions.getInstance())
                        .setHandler((p, entry) -> {
                            String result = entry.getLineWithoutColor(0).trim();
                            TaskUtils.runLater(() -> menuManager.inputResult(result), 1L);

                            return Collections.emptyList();
                        }).build();

                gui.open(player);
            } catch (Exception exception) {
                anvilInput(player, menuManager);
            }
        } else
            anvilInput(player, menuManager);
    }

    private void anvilInput(Player player, MenuManager menuManager) {
        String textType = menuManager.getClass().equals(AuctionsMenu.class) ? "text" : "number";

        try {
            AnvilGUI.Builder builder = new AnvilGUI.Builder()
                    .onClick((slot, state) -> {
                        if (slot != AnvilGUI.Slot.OUTPUT) {
                            return Collections.emptyList();
                        }

                        String result = state.getText().trim();
                        TaskUtils.runLater(() -> menuManager.inputResult(result), 1L);

                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                    }).text(Utils.colorize((DeluxeAuctions.getInstance().messagesFile.getString("input_lines.anvil." + textType))))
                    .preventClose()
                    .plugin(DeluxeAuctions.getInstance());

            if (TaskUtils.isFolia)
                builder.mainThreadExecutor((command) -> Bukkit.getGlobalRegionScheduler().execute(DeluxeAuctions.getInstance(), command));

            builder.open(player);
        } catch (Exception exception) {
            chatInput(player, menuManager);
        }
    }

    private void chatInput(Player player, MenuManager menuManager) {
        String textType = menuManager.getClass().equals(AuctionsMenu.class) ? "text" : "number";

        player.closeInventory();
        Utils.sendMessage(player, "input_lines.chat." + textType);

        new ChatInput(player, menuManager::inputResult);
    }
}