package main.walksy.lib.control.internal;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class ConnectionListener implements Listener, PluginMessageListener {
    private final ModControlImpl control;

    public ConnectionListener(final ModControlImpl control) {
        this.control = control;
    }

    @EventHandler
    public void onRegisterChannel(final PlayerRegisterChannelEvent event) {
        if (ControlProtocol.CONTROL_CHANNEL.equals(event.getChannel())) {
            this.control.onChannelRegistered(event.getPlayer());
        }
    }

    @EventHandler
    public void onUnregisterChannel(final PlayerUnregisterChannelEvent event) {
        if (ControlProtocol.CONTROL_CHANNEL.equals(event.getChannel())) {
            this.control.onChannelUnregistered(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(final PlayerChangedWorldEvent event) {
        this.control.markDirty(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        this.control.onQuit(event.getPlayer());
    }

    @Override
    public void onPluginMessageReceived(final String channel, final Player player, final byte[] message) {
        if (ControlProtocol.HELLO_CHANNEL.equals(channel)) {
            this.control.onHello(player, message);
        }
    }
}
