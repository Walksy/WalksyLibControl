package main.walksy.lib.control.command;

import org.bukkit.plugin.java.JavaPlugin;

public interface CommandRegistrar {
    String DESCRIPTION = "Switch WalksyLib client mods and features off and on.";

    boolean register(JavaPlugin plugin, ModControlCommand command);
}
