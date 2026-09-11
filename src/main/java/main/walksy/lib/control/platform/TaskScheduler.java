package main.walksy.lib.control.platform;

import org.bukkit.entity.Player;

public interface TaskScheduler {

    void run(Runnable task);

    void runFor(Player player, Runnable task);

    void cancelAll();
}
