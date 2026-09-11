package main.walksy.lib.control;

import main.walksy.lib.control.api.ModControl;
import main.walksy.lib.control.command.BukkitCommandRegistrar;
import main.walksy.lib.control.command.CommandRegistrar;
import main.walksy.lib.control.command.ModControlCommand;
import main.walksy.lib.control.internal.ConnectionListener;
import main.walksy.lib.control.internal.ControlProtocol;
import main.walksy.lib.control.internal.Messages;
import main.walksy.lib.control.internal.ModControlImpl;
import main.walksy.lib.control.platform.Platform;
import main.walksy.lib.control.platform.TaskScheduler;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;

import java.io.File;
import java.util.logging.Level;

public final class WalksyLibControlPlugin extends JavaPlugin {
    private static final String BRIGADIER_REGISTRAR = "main.walksy.lib.control.command.PaperBrigadierRegistrar";
    private static final String RULES_FILE = "rules.yml";
    private TaskScheduler scheduler;
    private ModControlImpl control;
    private volatile Messages messages;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        if (!new File(this.getDataFolder(), Messages.FILE_NAME).exists()) {
            this.saveResource(Messages.FILE_NAME, false);
        }
        this.scheduler = Platform.scheduler(this);
        this.messages = Messages.load(new File(this.getDataFolder(), Messages.FILE_NAME));
        final File rules = new File(this.getDataFolder(), RULES_FILE);
        this.control = new ModControlImpl(this, this.scheduler, rules, this.getConfig().getBoolean("log-client-reports", true));
        this.control.loadRules();
        if (!rules.exists()) {
            this.control.saveRules();
        }
        this.getServer().getServicesManager().register(ModControl.class, this.control, this, ServicePriority.Normal);

        final ConnectionListener listener = new ConnectionListener(this.control);
        final Messenger messenger = this.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(this, ControlProtocol.CONTROL_CHANNEL);
        messenger.registerIncomingPluginChannel(this, ControlProtocol.HELLO_CHANNEL, listener);
        this.getServer().getPluginManager().registerEvents(listener, this);

        this.registerCommand(new ModControlCommand(this.control, () -> this.messages, this::reloadEverything));
        this.control.adoptOnlinePlayers();
        this.getLogger().info("Running on " + Platform.describe() + ", WalksyLib protocol " + ControlProtocol.PROTOCOL);
    }

    @Override
    public void onDisable() {
        if (this.control != null) {
            this.control.saveIfDirty();
        }
        if (this.scheduler != null) {
            this.scheduler.cancelAll();
        }
    }

    public void reloadEverything() {
        this.reloadConfig();
        this.messages = Messages.load(new File(this.getDataFolder(), Messages.FILE_NAME));
        this.control.logClientReports(this.getConfig().getBoolean("log-client-reports", true));
        this.control.loadRules();
    }

    private void registerCommand(final ModControlCommand command) {
        if (Platform.hasPaperBrigadier()) {
            try {
                final CommandRegistrar registrar = (CommandRegistrar) Class.forName(BRIGADIER_REGISTRAR)
                        .getConstructor().newInstance();
                if (registrar.register(this, command)) {
                    return;
                }
            } catch (final ReflectiveOperationException | LinkageError | RuntimeException e) {
                this.getLogger().log(Level.WARNING, "Paper's command API was not usable using a plain Bukkit command", e);
            }
        }
        new BukkitCommandRegistrar().register(this, command);
    }
}
