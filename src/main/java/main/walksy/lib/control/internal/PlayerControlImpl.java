package main.walksy.lib.control.internal;

import main.walksy.lib.control.api.ClientInfo;
import main.walksy.lib.control.api.PlayerControl;
import main.walksy.lib.control.api.Restriction;
import main.walksy.lib.control.api.RuleSet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class PlayerControlImpl implements PlayerControl {
    private final ModControlImpl control;
    private final UUID uniqueId;

    PlayerControlImpl(final ModControlImpl control, final UUID uniqueId) {
        this.control = control;
        this.uniqueId = uniqueId;
    }

    @Override
    public UUID uniqueId() {
        return this.uniqueId;
    }

    @Override
    public @Nullable Player player() {
        final Player player = Bukkit.getPlayer(this.uniqueId);
        return player != null && player.isOnline() ? player : null;
    }

    @Override
    public boolean hasWalksyLib() {
        final Session session = this.control.session(this.uniqueId, false);
        return session != null && session.listening();
    }

    @Override
    public Optional<ClientInfo> client() {
        final Session session = this.control.session(this.uniqueId, false);
        return session == null ? Optional.empty() : Optional.ofNullable(session.client());
    }

    @Override
    public PlayerControl disable(final String modId) {
        return this.disable(Restriction.mod(modId));
    }

    @Override
    public PlayerControl disable(final String modId, final @Nullable String reason) {
        return this.disable(Restriction.mod(modId), reason);
    }

    @Override
    public PlayerControl disableFeature(final String modId, final String feature) {
        return this.disable(Restriction.feature(modId, feature));
    }

    @Override
    public PlayerControl disableFeature(final String modId, final String feature, final @Nullable String reason) {
        return this.disable(Restriction.feature(modId, feature), reason);
    }

    @Override
    public PlayerControl disable(final Restriction restriction) {
        final Session session = this.control.session(this.uniqueId, true);
        if (session != null) {
            session.own().disable(restriction);
        }
        return this;
    }

    @Override
    public PlayerControl disable(final Restriction restriction, final @Nullable String reason) {
        final Session session = this.control.session(this.uniqueId, true);
        if (session != null) {
            session.own().disable(restriction, reason);
        }
        return this;
    }

    @Override
    public PlayerControl enable(final String modId) {
        return this.enable(Restriction.mod(modId));
    }

    @Override
    public PlayerControl enableFeature(final String modId, final String feature) {
        return this.enable(Restriction.feature(modId, feature));
    }

    @Override
    public PlayerControl enable(final Restriction restriction) {
        final Session session = this.control.session(this.uniqueId, false);
        if (session != null) {
            session.own().enable(restriction);
        }
        return this;
    }

    @Override
    public PlayerControl enable(final Restriction restriction, final @Nullable String reason) {
        final Session session = this.control.session(this.uniqueId, false);
        if (session != null) {
            session.own().enable(restriction, reason);
        }
        return this;
    }

    @Override
    public PlayerControl clearMod(final String modId) {
        final Session session = this.control.session(this.uniqueId, false);
        if (session != null) {
            session.own().clearMod(modId);
        }
        return this;
    }

    @Override
    public Set<Restriction> ownRestrictions() {
        final Session session = this.control.session(this.uniqueId, false);
        return session == null ? Set.of() : session.own().restrictions();
    }

    @Override
    public PlayerControl apply(final RuleSet ruleSet) {
        final Player player = this.player();
        if (player != null) {
            ruleSet.applyTo(player);
        }
        return this;
    }

    @Override
    public PlayerControl apply(final String ruleSetId) {
        return this.apply(this.require(ruleSetId));
    }

    @Override
    public PlayerControl remove(final RuleSet ruleSet) {
        final Player player = this.player();
        if (player != null) {
            ruleSet.removeFrom(player);
        }
        return this;
    }

    @Override
    public PlayerControl remove(final String ruleSetId) {
        final RuleSetImpl ruleSet = this.control.findNamed(ruleSetId);
        return ruleSet == null ? this : this.remove(ruleSet);
    }

    @Override
    public Set<RuleSet> appliedRuleSets() {
        final Session session = this.control.session(this.uniqueId, false);
        if (session == null) {
            return Set.of();
        }
        final Set<RuleSet> applied = new LinkedHashSet<>();
        for (final String id : session.applied()) {
            final RuleSetImpl ruleSet = this.control.findNamed(id);
            if (ruleSet != null) {
                applied.add(ruleSet);
            }
        }
        return Collections.unmodifiableSet(applied);
    }

    @Override
    public Set<RuleSet> ruleSetsInForce() {
        final Player player = this.player();
        return player == null ? Set.of() : Collections.unmodifiableSet(this.control.ruleSetsInForce(player));
    }

    @Override
    public PlayerControl clear() {
        final Session session = this.control.session(this.uniqueId, false);
        if (session != null) {
            session.own().clear();
            if (!session.applied().isEmpty()) {
                session.applied().clear();
                this.control.markDirty(this.uniqueId);
            }
        }
        return this;
    }

    @Override
    public Set<Restriction> effective() {
        final Player player = this.player();
        return player == null ? Set.of() : this.control.compute(player).restrictions();
    }

    @Override
    public boolean isRestricted(final Restriction restriction) {
        final Player player = this.player();
        return player != null && this.control.compute(player).isRestricted(restriction);
    }

    @Override
    public boolean isBypassing() {
        final Player player = this.player();
        return player != null && player.hasPermission(ModControlImpl.BYPASS_PERMISSION);
    }

    private RuleSet require(final String ruleSetId) {
        final RuleSetImpl ruleSet = this.control.findNamed(ruleSetId);
        if (ruleSet == null) {
            throw new IllegalArgumentException("No rule set \"" + ruleSetId + "\"");
        }
        return ruleSet;
    }
}
