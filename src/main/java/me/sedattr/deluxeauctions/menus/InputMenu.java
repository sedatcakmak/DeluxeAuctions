package me.sedattr.deluxeauctions.menus;

import de.rapha149.signgui.SignGUI;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.sedattr.auctionsapi.events.InputOpenEvent;
import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.deluxeauctions.others.ChatInput;
import me.sedattr.deluxeauctions.others.TaskUtils;
import me.sedattr.deluxeauctions.others.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import net.wesjd.anvilgui.AnvilGUI;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class InputMenu {
    // Minecraft 1.21.6 = protocol 771; the dialog input API requires this client version or newer.
    private static final int DIALOG_MIN_PROTOCOL = 771;

    private String type;

    public InputMenu() {
        this.type = DeluxeAuctions.getInstance().configFile.getString("settings.input_type");
        if (this.type == null)
            this.type = "auto";
    }

    public void open(Player player, MenuManager menuManager) {
        InputOpenEvent event = new InputOpenEvent(player, menuManager.getMenuName());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return;

        String effective = this.type;
        if (effective.equalsIgnoreCase("auto"))
            effective = resolveAutoType(player);

        if (effective.equalsIgnoreCase("chat"))
            chatInput(player, menuManager);
        else if (effective.equalsIgnoreCase("anvil"))
            anvilInput(player, menuManager);
        else if (effective.equalsIgnoreCase("dialog"))
            dialogInput(player, menuManager);
        else
            signInput(player, menuManager);
    }

    // Picks the best input for the player's client:
    // - Geyser (Bedrock) clients -> chat (sign/anvil/dialog render poorly)
    // - Java clients below 1.21.6 (only reachable via ViaVersion) -> sign GUI
    // - Java clients 1.21.6+ -> dialog
    private String resolveAutoType(Player player) {
        if (isGeyser(player))
            return "chat";
        if (!supportsDialog(player))
            return "sign";
        return "dialog";
    }

    private boolean isGeyser(Player player) {
        try {
            String brand = player.getClientBrandName();
            return brand != null && brand.toLowerCase().contains("geyser");
        } catch (Throwable ignored) {
            return false;
        }
    }

    // Resolves the client protocol via ViaVersion (soft dependency, reflection).
    // When ViaVersion is absent the server is native, so every client is modern -> dialog capable.
    private boolean supportsDialog(Player player) {
        try {
            Class<?> via = Class.forName("com.viaversion.viaversion.api.Via");
            Object api = via.getMethod("getAPI").invoke(null);
            Object protocol = api.getClass().getMethod("getPlayerVersion", UUID.class).invoke(api, player.getUniqueId());
            if (protocol instanceof Number number) {
                int version = number.intValue();
                if (version > 0)
                    return version >= DIALOG_MIN_PROTOCOL;
            }
        } catch (Throwable ignored) {
            // ViaVersion not installed or lookup failed -> assume native modern client
        }
        return true;
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
            } catch (Throwable throwable) {
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
        } catch (Throwable throwable) {
            chatInput(player, menuManager);
        }
    }

    // Native Minecraft dialog (1.21.6+). Sign-only decoration lines (e.g. "^^^^^") are hidden here.
    private void dialogInput(Player player, MenuManager menuManager) {
        boolean isText = menuManager.getClass().equals(AuctionsMenu.class);
        String textType = isText ? "text" : "number";
        FileConfiguration messages = DeluxeAuctions.getInstance().messagesFile;
        String path = "input_lines.dialog." + textType;

        String titleStr = messages.getString(path + ".title");
        String buttonStr = messages.getString(path + ".button");

        if (titleStr == null || titleStr.isEmpty()) {
            for (String line : messages.getStringList("input_lines.sign." + textType)) {
                if (line == null)
                    continue;

                String trimmed = line.trim();
                if (trimmed.isEmpty() || isSignDecoration(trimmed))
                    continue;

                titleStr = line;
                break;
            }
        }
        if (titleStr == null || titleStr.isEmpty())
            titleStr = isText ? "&aEnter Text" : "&aEnter Number";
        if (buttonStr == null || buttonStr.isEmpty())
            buttonStr = "&aConfirm";

        try {
            Component title = toComponent(titleStr);

            TextDialogInput input = DialogInput.text("da_input", title)
                    .labelVisible(false)
                    .width(300)
                    .maxLength(128)
                    .build();

            ActionButton submit = ActionButton.builder(toComponent(buttonStr))
                    .action(DialogAction.customClick((view, audience) -> {
                        String raw = view.getText("da_input");
                        String result = (raw == null ? "" : raw).trim();
                        TaskUtils.runLater(() -> menuManager.inputResult(result), 1L);
                    }, ClickCallback.Options.builder().build()))
                    .build();

            DialogBase base = DialogBase.builder(title)
                    .canCloseWithEscape(true)
                    .inputs(Collections.singletonList(input))
                    .build();

            Dialog dialog = Dialog.create(builder -> builder.empty().base(base).type(DialogType.notice(submit)));
            player.showDialog(dialog);
        } catch (Throwable throwable) {
            chatInput(player, menuManager);
        }
    }

    private void chatInput(Player player, MenuManager menuManager) {
        String textType = menuManager.getClass().equals(AuctionsMenu.class) ? "text" : "number";

        player.closeInventory();
        Utils.sendMessage(player, "input_lines.chat." + textType);

        new ChatInput(player, menuManager::inputResult);
    }

    // Detects sign-only pointer/decoration lines (e.g. "^^^^^^^^^^") so they are not shown in dialogs.
    private boolean isSignDecoration(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty())
            return false;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c != '^' && c != '~' && c != '-' && c != '_' && c != '=' && c != '*')
                return false;
        }
        return true;
    }

    private Component toComponent(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy == null ? "" : legacy);
    }
}
