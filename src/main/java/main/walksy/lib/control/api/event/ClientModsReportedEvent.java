package main.walksy.lib.control.api.event;

import main.walksy.lib.control.api.ClientInfo;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public final class ClientModsReportedEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ClientInfo client;

    public ClientModsReportedEvent(final Player player, final ClientInfo client) {
        super(player);
        this.client = client;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public ClientInfo getClient() {
        return this.client;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
