package main.walksy.lib.control.command;

import org.bukkit.command.CommandSender;

import java.util.List;

public final class ControlPermissions {
    public static final String ADMIN = "wlcontrol.admin";
    public static final String STATUS = "wlcontrol.command.status";
    public static final String EDIT = "wlcontrol.command.edit";
    public static final String PLAYER = "wlcontrol.command.player";
    public static final String RULE_SET = "wlcontrol.command.ruleset";
    public static final String RELOAD = "wlcontrol.command.reload";
    public static final List<String> COMMANDS = List.of(STATUS, EDIT, PLAYER, RULE_SET, RELOAD);

    private ControlPermissions() {
    }

    public static boolean any(final CommandSender sender) {
        for (final String permission : COMMANDS) {
            if (sender.hasPermission(permission)) {
                return true;
            }
        }
        return sender.hasPermission(ADMIN);
    }

    public static boolean has(final CommandSender sender, final String permission) {
        return sender.hasPermission(permission) || sender.hasPermission(ADMIN);
    }
}
