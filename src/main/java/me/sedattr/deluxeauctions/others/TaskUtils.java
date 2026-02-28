package me.sedattr.deluxeauctions.others;

import me.sedattr.deluxeauctions.DeluxeAuctions;
import me.sedattr.deluxeauctions.inventoryapi.HInventory;
import me.sedattr.deluxeauctions.inventoryapi.inventory.InventoryAPI;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public final class TaskUtils {
    public static boolean isFolia;

    static {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (final ClassNotFoundException e) {
            isFolia = false;
        }
    }

    public static void run(Runnable runnable) {
        if (isFolia) {
            DeluxeAuctions.getInstance().getServer().getGlobalRegionScheduler().execute(DeluxeAuctions.getInstance(), runnable);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    runnable.run();
                }
            }.runTask(DeluxeAuctions.getInstance());
        }
    }

    public static void runAsync(Runnable runnable) {
        if (isFolia) {
            DeluxeAuctions.getInstance().getServer().getAsyncScheduler().runNow(DeluxeAuctions.getInstance(), scheduledTask -> runnable.run());
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    runnable.run();
                }
            }.runTaskAsynchronously(DeluxeAuctions.getInstance());
        }
    }

    public static void runLater(Runnable runnable, long delayTicks) {
        if (isFolia) {
            DeluxeAuctions.getInstance().getServer().getGlobalRegionScheduler().runDelayed(DeluxeAuctions.getInstance(), task -> runnable.run(), delayTicks);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    runnable.run();
                }
            }.runTaskLater(DeluxeAuctions.getInstance(), delayTicks);
        }
    }

    public static void runLaterAsync(Runnable runnable, long delayTicks) {
        if (isFolia) {
            DeluxeAuctions.getInstance().getServer().getAsyncScheduler().runDelayed(DeluxeAuctions.getInstance(), scheduledTask -> runnable.run(), delayTicks * 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    runnable.run();
                }
            }.runTaskLaterAsynchronously(DeluxeAuctions.getInstance(), delayTicks);
        }
    }

    public static void runTimerAsync(Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia) {
            DeluxeAuctions.getInstance().getServer().getAsyncScheduler().runAtFixedRate(DeluxeAuctions.getInstance(), scheduledTask -> runnable.run(), delayTicks * 50, periodTicks * 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    runnable.run();
                }
            }.runTaskTimerAsynchronously(DeluxeAuctions.getInstance(), delayTicks, periodTicks);
        }
    }

    public static void runTimerAsync(Player player, String id, Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia) {
            DeluxeAuctions.getInstance().getServer().getAsyncScheduler().runAtFixedRate(DeluxeAuctions.getInstance(), task -> {
                HInventory inventory = InventoryAPI.getInventory(player);
                if (inventory == null) {
                    cancelTask(task);
                    return;
                }

                String inventoryId = inventory.getId();
                if (!inventoryId.equalsIgnoreCase(id)) {
                    if (id.equalsIgnoreCase("auctions") && inventoryId.equalsIgnoreCase("search")) {
                        runnable.run();
                        return;
                    }

                    cancelTask(task);
                    return;
                }

                runnable.run();
            }, delayTicks * 50, periodTicks * 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    HInventory inventory = InventoryAPI.getInventory(player);
                    if (inventory == null) {
                        cancel();
                        return;
                    }

                    String inventoryId = inventory.getId();
                    if (!inventoryId.equalsIgnoreCase(id)) {
                        if (id.equalsIgnoreCase("auctions") && inventoryId.equalsIgnoreCase("search")) {
                            runnable.run();
                            return;
                        }

                        cancel();
                        return;
                    }

                    runnable.run();
                }
            }.runTaskTimerAsynchronously(DeluxeAuctions.getInstance(), delayTicks, periodTicks);
        }
    }

    private static void cancelTask(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
        if (!isFolia)
            return;

        task.cancel();
    }
}