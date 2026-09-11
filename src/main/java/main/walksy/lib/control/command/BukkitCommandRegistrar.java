package main.walksy.lib.control.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class BukkitCommandRegistrar implements CommandRegistrar {

    @Override
    public boolean register(final JavaPlugin plugin, final ModControlCommand command) {
        try {
            final Method getter = Bukkit.getServer().getClass().getMethod("getCommandMap");
            final CommandMap map = (CommandMap) getter.invoke(Bukkit.getServer());
            map.register(plugin.getName().toLowerCase(Locale.ROOT), new RootCommand(command));
            return true;
        } catch (final ReflectiveOperationException | ClassCastException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not register /" + ModControlCommand.NAME, e);
            return false;
        }
    }

    private static final class RootCommand extends Command {
        private final ModControlCommand command;

        private RootCommand(final ModControlCommand command) {
            super(ModControlCommand.NAME, DESCRIPTION, "/" + ModControlCommand.NAME + " help", ModControlCommand.ALIASES);
            this.command = command;
            this.setPermission(String.join(";", ControlPermissions.COMMANDS) + ";" + ControlPermissions.ADMIN);
        }

        @Override
        public boolean execute(final CommandSender sender, final String label, final String[] args) {
            this.command.execute(sender, label, args);
            return true;
        }

        @Override
        public List<String> tabComplete(final CommandSender sender, final String alias, final String[] args) {
            return this.command.complete(sender, alias, args);
        }
    }
}
