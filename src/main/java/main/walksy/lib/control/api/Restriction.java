package main.walksy.lib.control.api;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record Restriction(String modId, @Nullable String feature) implements Comparable<Restriction> {
    public static final int MAX_ID_LENGTH = 64;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1," + MAX_ID_LENGTH + "}");
    private static final Comparator<Restriction> ORDER = Comparator.comparing(Restriction::modId)
            .thenComparing(Restriction::feature, Comparator.nullsFirst(Comparator.naturalOrder()));

    public Restriction {
        modId = normalize(modId, "mod id");
        feature = feature == null ? null : normalize(feature, "feature id");
    }

    public static Restriction mod(final String modId) {
        return new Restriction(modId, null);
    }

    public static Restriction feature(final String modId, final String feature) {
        return new Restriction(modId, Objects.requireNonNull(feature, "feature"));
    }

    public static Restriction parse(final String text) {
        Objects.requireNonNull(text, "text");
        final int colon = text.indexOf(':');
        return colon < 0
                ? mod(text.trim())
                : feature(text.substring(0, colon).trim(), text.substring(colon + 1).trim());
    }

    public static boolean isValidId(final @Nullable String id) {
        return id != null && ID.matcher(id.toLowerCase(Locale.ROOT)).matches();
    }

    public boolean isWholeMod() {
        return this.feature == null;
    }

    @Override
    public int compareTo(final Restriction other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return this.feature == null ? this.modId : this.modId + ":" + this.feature;
    }

    private static String normalize(final String id, final String what) {
        Objects.requireNonNull(id, what);
        final String lower = id.toLowerCase(Locale.ROOT);
        if (!ID.matcher(lower).matches()) {
            throw new IllegalArgumentException("Invalid " + what + " \"" + id + "\": use 1-" + MAX_ID_LENGTH
                    + " characters of a-z, 0-9, '_', '.' or '-'");
        }
        return lower;
    }
}
