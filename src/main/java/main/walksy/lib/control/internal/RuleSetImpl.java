package main.walksy.lib.control.internal;

import main.walksy.lib.control.api.Restriction;
import main.walksy.lib.control.api.RuleSet;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

final class RuleSetImpl implements RuleSet {
    private final ModControlImpl control;
    private final String id;
    private final Kind kind;
    private final @Nullable UUID owner;
    private final Map<Restriction, String> restrictions = new ConcurrentSkipListMap<>();
    private final Set<String> worlds = new ConcurrentSkipListSet<>();
    private volatile @Nullable String permission;
    private volatile boolean persistent;
    private volatile boolean deleted;

    private RuleSetImpl(final ModControlImpl control, final String id, final Kind kind, final @Nullable UUID owner, final boolean persistent) {
        this.control = control;
        this.id = id;
        this.kind = kind;
        this.owner = owner;
        this.persistent = persistent;
    }

    static RuleSetImpl global(final ModControlImpl control) {
        return new RuleSetImpl(control, ModControlImpl.GLOBAL_ID, Kind.GLOBAL, null, true);
    }

    static RuleSetImpl named(final ModControlImpl control, final String id, final boolean persistent) {
        return new RuleSetImpl(control, id, Kind.NAMED, null, persistent);
    }

    static RuleSetImpl personal(final ModControlImpl control, final UUID owner) {
        return new RuleSetImpl(control, "player:" + owner, Kind.PERSONAL, owner, false);
    }

    enum Kind {
        GLOBAL,
        NAMED,
        PERSONAL
    }

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public boolean isGlobal() {
        return this.kind == Kind.GLOBAL;
    }

    Kind kind() {
        return this.kind;
    }

    @Nullable UUID owner() {
        return this.owner;
    }

    @Override
    public RuleSet disable(final String modId) {
        return this.disable(Restriction.mod(modId));
    }

    @Override
    public RuleSet disable(final String modId, final @Nullable String reason) {
        return this.disable(Restriction.mod(modId), reason);
    }

    @Override
    public RuleSet disableFeature(final String modId, final String feature) {
        return this.disable(Restriction.feature(modId, feature));
    }

    @Override
    public RuleSet disableFeature(final String modId, final String feature, final @Nullable String reason) {
        return this.disable(Restriction.feature(modId, feature), reason);
    }

    @Override
    public RuleSet disable(final Restriction restriction) {
        if (this.restrictions.putIfAbsent(Objects.requireNonNull(restriction, "restriction"), "") == null) {
            this.changed();
        }
        return this;
    }

    @Override
    public RuleSet disable(final Restriction restriction, final @Nullable String reason) {
        final String next = normalizeReason(reason);
        final String previous = this.restrictions.put(Objects.requireNonNull(restriction, "restriction"), next);
        if (!next.equals(previous)) {
            this.changed();
        }
        return this;
    }

    @Override
    public RuleSet disableAll(final Collection<Restriction> restrictions) {
        boolean changed = false;
        for (final Restriction restriction : restrictions) {
            changed |= this.restrictions.putIfAbsent(Objects.requireNonNull(restriction, "restriction"), "") == null;
        }
        if (changed) {
            this.changed();
        }
        return this;
    }

    @Override
    public RuleSet enable(final String modId) {
        return this.enable(Restriction.mod(modId));
    }

    @Override
    public RuleSet enableFeature(final String modId, final String feature) {
        return this.enable(Restriction.feature(modId, feature));
    }

    @Override
    public RuleSet enable(final Restriction restriction) {
        if (this.restrictions.remove(Objects.requireNonNull(restriction, "restriction")) != null) {
            this.changed();
        }
        return this;
    }

    @Override
    public RuleSet enable(final Restriction restriction, final @Nullable String reason) {
        if (this.restrictions.containsKey(Objects.requireNonNull(restriction, "restriction"))) {
            this.control.noteLift(restriction, reason);
        }
        return this.enable(restriction);
    }

    @Override
    public RuleSet clearMod(final String modId) {
        final String normalized = Restriction.mod(modId).modId();
        if (this.restrictions.keySet().removeIf(restriction -> restriction.modId().equals(normalized))) {
            this.changed();
        }
        return this;
    }

    @Override
    public RuleSet clear() {
        if (!this.restrictions.isEmpty()) {
            this.restrictions.clear();
            this.changed();
        }
        return this;
    }

    @Override
    public Set<Restriction> restrictions() {
        return Collections.unmodifiableSet(new TreeSet<>(this.restrictions.keySet()));
    }

    @Override
    public boolean contains(final Restriction restriction) {
        return this.restrictions.containsKey(restriction);
    }

    @Override
    public String reasonFor(final Restriction restriction) {
        final String reason = this.restrictions.get(restriction);
        return reason == null ? "" : reason;
    }

    @Override
    public RuleSet applyTo(final Player player) {
        this.requireNamed("applied to a player");
        this.control.apply(player, this);
        return this;
    }

    @Override
    public RuleSet removeFrom(final Player player) {
        this.control.remove(player, this);
        return this;
    }

    @Override
    public boolean isAppliedTo(final Player player) {
        return this.control.isApplied(player, this);
    }

    @Override
    public Collection<Player> appliedPlayers() {
        return this.control.appliedPlayers(this);
    }

    @Override
    public RuleSet bindWorld(final String worldName) {
        this.requireNamed("bound to a world");
        if (this.worlds.add(Objects.requireNonNull(worldName, "worldName"))) {
            this.changed();
        }
        return this;
    }

    @Override
    public RuleSet unbindWorld(final String worldName) {
        if (this.worlds.remove(worldName)) {
            this.changed();
        }
        return this;
    }

    @Override
    public Set<String> worlds() {
        return Collections.unmodifiableSet(new TreeSet<>(this.worlds));
    }

    @Override
    public RuleSet bindPermission(final @Nullable String permission) {
        final String next = permission == null || permission.isBlank() ? null : permission.strip();
        if (next != null) {
            this.requireNamed("bound to a permission");
        }
        if (!Objects.equals(next, this.permission)) {
            this.permission = next;
            this.changed();
        }
        return this;
    }

    @Override
    public @Nullable String permission() {
        return this.permission;
    }

    @Override
    public boolean isInForceFor(final Player player) {
        return this.control.ruleSetsInForce(player).contains(this);
    }

    boolean boundTo(final Player player) {
        if (this.worlds.contains(player.getWorld().getName())) {
            return true;
        }
        final String node = this.permission;
        return node != null && player.hasPermission(node);
    }

    @Override
    public boolean isPersistent() {
        return this.kind == Kind.GLOBAL || this.persistent;
    }

    @Override
    public RuleSet persistent(final boolean persistent) {
        this.requireNamed("made (non-)persistent");
        if (this.persistent != persistent) {
            this.persistent = persistent;
            this.control.markStorageDirty();
        }
        return this;
    }

    boolean isDeleted() {
        return this.deleted;
    }

    void markDeleted() {
        this.deleted = true;
    }

    void load(final Map<Restriction, String> restrictions, final List<String> worlds, final @Nullable String permission) {
        this.restrictions.clear();
        for (final Map.Entry<Restriction, String> restriction : restrictions.entrySet()) {
            this.restrictions.put(restriction.getKey(), normalizeReason(restriction.getValue()));
        }
        this.worlds.clear();
        if (this.kind == Kind.NAMED) {
            this.worlds.addAll(worlds);
            this.permission = permission == null || permission.isBlank() ? null : permission.strip();
        }
        this.persistent = true;
    }

    private void changed() {
        this.control.ruleSetChanged(this);
    }

    static String normalizeReason(final @Nullable String reason) {
        if (reason == null) {
            return "";
        }
        final String stripped = reason.strip();
        return stripped.length() <= ControlProtocol.MAX_REASON_LENGTH
                ? stripped
                : stripped.substring(0, ControlProtocol.MAX_REASON_LENGTH);
    }

    private void requireNamed(final String action) {
        if (this.kind != Kind.NAMED) {
            throw new UnsupportedOperationException("The " + (this.kind == Kind.GLOBAL ? "global" : "per-player")
                    + " rule set cannot be " + action);
        }
        if (this.deleted) {
            throw new IllegalStateException("Rule set \"" + this.id + "\" was deleted");
        }
    }

    @Override
    public String toString() {
        return "RuleSet[" + this.id + "]";
    }
}
