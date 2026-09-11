package main.walksy.lib.control.api.event;

import main.walksy.lib.control.api.Restriction;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import java.util.Set;

public final class PlayerRestrictionsChangeEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Set<Restriction> previous;
    private final Set<Restriction> current;

    public PlayerRestrictionsChangeEvent(final Player player, final Set<Restriction> previous, final Set<Restriction> current) {
        super(player);
        this.previous = Set.copyOf(previous);
        this.current = Set.copyOf(current);
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public Set<Restriction> getPrevious() {
        return this.previous;
    }

    public Set<Restriction> getCurrent() {
        return this.current;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
