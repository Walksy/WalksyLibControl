package main.walksy.lib.control.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class BukkitTaskScheduler implements TaskScheduler {
    private final Plugin plugin;

    public BukkitTaskScheduler(final Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run(final Runnable task) {
        Bukkit.getScheduler().runTask(this.plugin, task);
    }

    @Override
    public void runFor(final Player player, final Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this.plugin, task);
        }
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(this.plugin);
    }
}
