package main.walksy.lib.control.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PlayerControl {

    UUID uniqueId();

    @Nullable Player player();

    boolean hasWalksyLib();

    Optional<ClientInfo> client();

    PlayerControl disable(String modId);

    PlayerControl disable(String modId, @Nullable String reason);

    PlayerControl disableFeature(String modId, String feature);

    PlayerControl disableFeature(String modId, String feature, @Nullable String reason);

    PlayerControl disable(Restriction restriction);

    PlayerControl disable(Restriction restriction, @Nullable String reason);

    PlayerControl enable(String modId);

    PlayerControl enableFeature(String modId, String feature);

    PlayerControl enable(Restriction restriction);

    PlayerControl enable(Restriction restriction, @Nullable String reason);

    PlayerControl clearMod(String modId);

    Set<Restriction> ownRestrictions();

    PlayerControl apply(RuleSet ruleSet);

    PlayerControl apply(String ruleSetId);

    PlayerControl remove(RuleSet ruleSet);

    PlayerControl remove(String ruleSetId);

    Set<RuleSet> appliedRuleSets();

    Set<RuleSet> ruleSetsInForce();

    PlayerControl clear();

    Set<Restriction> effective();

    boolean isRestricted(Restriction restriction);

    default boolean isModDisabled(final String modId) {
        return this.isRestricted(Restriction.mod(modId));
    }

    default boolean isFeatureDisabled(final String modId, final String feature) {
        return this.isRestricted(Restriction.feature(modId, feature));
    }

    boolean isBypassing();
}
