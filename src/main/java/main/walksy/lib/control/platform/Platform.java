package main.walksy.lib.control.platform;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

public final class Platform {
    private static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";
    private static final String BRIGADIER_MARKER = "io.papermc.paper.command.brigadier.Commands";
    private static final String FOLIA_SCHEDULER = "main.walksy.lib.control.platform.FoliaTaskScheduler";

    private Platform() {
    }

    public static boolean isFolia() {
        return hasClass(FOLIA_MARKER);
    }

    public static boolean hasPaperBrigadier() {
        return hasClass(BRIGADIER_MARKER);
    }

    public static String describe() {
        return Bukkit.getName() + " (" + (isFolia() ? "Folia region scheduling" : "main-thread scheduling") + ", "
                + (hasPaperBrigadier() ? "Brigadier command" : "Bukkit command") + ")";
    }

    public static TaskScheduler scheduler(final Plugin plugin) {
        if (isFolia()) {
            try {
                return (TaskScheduler) Class.forName(FOLIA_SCHEDULER).getConstructor(Plugin.class).newInstance(plugin);
            } catch (final ReflectiveOperationException | LinkageError e) {
                plugin.getLogger().log(Level.SEVERE, "Running on Folia but its scheduler could not be set up", e);
            }
        }
        return new BukkitTaskScheduler(plugin);
    }

    private static boolean hasClass(final String name) {
        try {
            Class.forName(name, false, Platform.class.getClassLoader());
            return true;
        } catch (final ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
