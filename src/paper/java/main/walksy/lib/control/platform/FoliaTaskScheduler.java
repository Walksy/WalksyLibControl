package main.walksy.lib.control.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class FoliaTaskScheduler implements TaskScheduler {
    private final Plugin plugin;

    public FoliaTaskScheduler(final Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run(final Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(this.plugin, scheduled -> task.run());
    }

    @Override
    public void runFor(final Player player, final Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            task.run();
        } else {
            player.getScheduler().run(this.plugin, scheduled -> task.run(), null);
        }
    }

    @Override
    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(this.plugin);
        Bukkit.getAsyncScheduler().cancelTasks(this.plugin);
    }
}
