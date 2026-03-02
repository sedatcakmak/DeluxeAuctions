package me.sedattr.deluxeauctions.economy;

import org.bukkit.OfflinePlayer;
import ch.njol.skript.variables.Variables;

public class SkriptEconomy implements EconomyManager {
    private final String variable;

    public SkriptEconomy(String variable) {
        this.variable = variable;
    }

    @Override
    public boolean addBalance(OfflinePlayer player, Double count) {
        if (player == null || count == null || count < 0)
            return false;

        double newBalance = getBalance(player) + count;
        setBalance(player, newBalance);

        return true;
    }

    @Override
    public boolean removeBalance(OfflinePlayer player, Double count) {
        if (player == null || count == null || count < 0)
            return false;

        if (getBalance(player) < count)
            return false;

        double newBalance = getBalance(player) - count;
        setBalance(player, newBalance);

        return true;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (player == null)
            return 0;
        if (player.getName() == null || player.getName().isEmpty())
            return 0;

        Object balanceObj = Variables.getVariable(this.variable
                .replace("%player_name%", player.getName())
                .replace("%player_uuid%", player.getUniqueId().toString()), null, false);

        if (balanceObj == null)
            return 0;
        if (balanceObj instanceof Number)
            return ((Number) balanceObj).doubleValue();

        return 0;
    }

    private void setBalance(OfflinePlayer player, double amount) {
        if (player == null)
            return;
        if (player.getName() == null || player.getName().isEmpty())
            return;

        Variables.setVariable(this.variable
                .replace("%player_name%", player.getName())
                .replace("%player_uuid%", player.getUniqueId().toString()), amount, null, false);
    }
}