package main.walksy.lib.control.api;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public interface RuleSet {

    String id();

    boolean isGlobal();

    RuleSet disable(String modId);

    RuleSet disable(String modId, @Nullable String reason);

    RuleSet disableFeature(String modId, String feature);

    RuleSet disableFeature(String modId, String feature, @Nullable String reason);

    RuleSet disable(Restriction restriction);

    RuleSet disable(Restriction restriction, @Nullable String reason);

    RuleSet disableAll(Collection<Restriction> restrictions);

    RuleSet enable(String modId);

    RuleSet enableFeature(String modId, String feature);

    RuleSet enable(Restriction restriction);

    RuleSet enable(Restriction restriction, @Nullable String reason);

    RuleSet clearMod(String modId);

    RuleSet clear();

    Set<Restriction> restrictions();

    boolean contains(Restriction restriction);

    String reasonFor(Restriction restriction);

    RuleSet applyTo(Player player);

    RuleSet removeFrom(Player player);

    boolean isAppliedTo(Player player);

    Collection<Player> appliedPlayers();

    RuleSet bindWorld(String worldName);

    default RuleSet bindWorld(final World world) {
        return this.bindWorld(world.getName());
    }

    RuleSet unbindWorld(String worldName);

    default RuleSet unbindWorld(final World world) {
        return this.unbindWorld(world.getName());
    }

    Set<String> worlds();

    RuleSet bindPermission(@Nullable String permission);

    @Nullable String permission();

    boolean isInForceFor(Player player);

    boolean isPersistent();

    RuleSet persistent(boolean persistent);
}
