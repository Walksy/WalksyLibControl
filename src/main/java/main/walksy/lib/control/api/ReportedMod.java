package main.walksy.lib.control.api;

import java.util.List;

public record ReportedMod(String modId, String displayName, boolean wholeMod, List<String> features) {

    public ReportedMod {
        features = List.copyOf(features);
    }

    public boolean allows(final Restriction restriction) {
        if (!this.modId.equals(restriction.modId())) {
            return false;
        }
        return restriction.isWholeMod() ? this.wholeMod : this.features.contains(restriction.feature());
    }
}
