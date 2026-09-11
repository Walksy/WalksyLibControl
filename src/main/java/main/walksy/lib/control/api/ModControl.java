package main.walksy.lib.control.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;

public interface ModControl {

    static ModControl get() {
        final ModControl control = Bukkit.getServicesManager().load(ModControl.class);
        if (control == null) {
            throw new IllegalStateException("WalksyLibControl is not enabled. Add it to depend/softdepend in your plugin.yml so it loads before your plugin.");
        }
        return control;
    }

    RuleSet global();

    RuleSet ruleSet(String id);

    Optional<RuleSet> findRuleSet(String id);

    Collection<RuleSet> ruleSets();

    boolean deleteRuleSet(String id);

    PlayerControl player(Player player);

    default Optional<ClientInfo> client(final Player player) {
        return this.player(player).client();
    }

    default boolean hasWalksyLib(final Player player) {
        return this.player(player).hasWalksyLib();
    }

    int protocolVersion();
}
