package main.walksy.lib.control.internal;

import main.walksy.lib.control.api.Restriction;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

public final class Snapshot {
    public static final Snapshot EMPTY = new Snapshot(new TreeMap<>());
    private static final String REASON_SEPARATOR = " / ";
    private final SortedMap<String, ModEntry> mods;

    private Snapshot(final SortedMap<String, ModEntry> mods) {
        this.mods = Collections.unmodifiableSortedMap(mods);
    }

    public record ModEntry(boolean wholeMod, String modReason, SortedMap<String, String> features) {
    }

    public static Builder builder() {
        return new Builder();
    }

    public SortedMap<String, ModEntry> mods() {
        return this.mods;
    }

    public boolean isEmpty() {
        return this.mods.isEmpty();
    }

    public boolean isRestricted(final Restriction restriction) {
        final ModEntry entry = this.mods.get(restriction.modId());
        if (entry == null) {
            return false;
        }
        return entry.wholeMod() || (!restriction.isWholeMod() && entry.features().containsKey(restriction.feature()));
    }

    public Set<Restriction> restrictions() {
        final Set<Restriction> restrictions = new TreeSet<>();
        for (final Map.Entry<String, ModEntry> mod : this.mods.entrySet()) {
            if (mod.getValue().wholeMod()) {
                restrictions.add(Restriction.mod(mod.getKey()));
            }
            for (final String feature : mod.getValue().features().keySet()) {
                restrictions.add(Restriction.feature(mod.getKey(), feature));
            }
        }
        return Collections.unmodifiableSet(restrictions);
    }

    public byte[] encode() {
        return ControlProtocol.encode(this, Map.of());
    }

    public byte[] encode(final Map<Restriction, String> lifted) {
        return ControlProtocol.encode(this, lifted);
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof final Snapshot snapshot && this.mods.equals(snapshot.mods);
    }

    @Override
    public int hashCode() {
        return this.mods.hashCode();
    }

    @Override
    public String toString() {
        return this.restrictions().toString();
    }

    public static final class Builder {
        private final Map<String, Pending> mods = new TreeMap<>();

        private Builder() {
        }

        private static final class Pending {
            private final Set<String> modReasons = new LinkedHashSet<>();
            private final Map<String, Set<String>> features = new TreeMap<>();
            private boolean wholeMod;
        }

        public Builder add(final Restriction restriction, final String reason) {
            final Pending pending = this.mods.computeIfAbsent(restriction.modId(), ignored -> new Pending());
            final Set<String> reasons;
            if (restriction.isWholeMod()) {
                pending.wholeMod = true;
                reasons = pending.modReasons;
            } else {
                reasons = pending.features.computeIfAbsent(restriction.feature(), ignored -> new LinkedHashSet<>());
            }
            if (reason != null && !reason.isBlank()) {
                reasons.add(reason.strip());
            }
            return this;
        }

        public Builder add(final Collection<Restriction> restrictions, final String reason) {
            for (final Restriction restriction : restrictions) {
                this.add(restriction, reason);
            }
            return this;
        }

        public Snapshot build() {
            if (this.mods.isEmpty()) {
                return EMPTY;
            }
            final SortedMap<String, ModEntry> built = new TreeMap<>();
            for (final Map.Entry<String, Pending> mod : this.mods.entrySet()) {
                final Pending pending = mod.getValue();
                final SortedMap<String, String> features = new TreeMap<>();
                for (final Map.Entry<String, Set<String>> feature : pending.features.entrySet()) {
                    features.put(feature.getKey(), join(feature.getValue()));
                }
                built.put(mod.getKey(), new ModEntry(pending.wholeMod, join(pending.modReasons),
                        Collections.unmodifiableSortedMap(features)));
            }
            return new Snapshot(built);
        }

        private static String join(final Set<String> reasons) {
            final String joined = String.join(REASON_SEPARATOR, reasons);
            return joined.length() <= ControlProtocol.MAX_REASON_LENGTH
                    ? joined
                    : joined.substring(0, ControlProtocol.MAX_REASON_LENGTH - 1) + "…";
        }
    }
}
