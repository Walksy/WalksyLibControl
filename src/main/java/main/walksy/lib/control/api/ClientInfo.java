package main.walksy.lib.control.api;

import java.util.List;
import java.util.Optional;

public record ClientInfo(int protocol, List<ReportedMod> mods) {

    public ClientInfo {
        mods = List.copyOf(mods);
    }

    public Optional<ReportedMod> mod(final String modId) {
        for (final ReportedMod mod : this.mods) {
            if (mod.modId().equals(modId)) {
                return Optional.of(mod);
            }
        }
        return Optional.empty();
    }

    public boolean hasMod(final String modId) {
        return this.mod(modId).isPresent();
    }

    public boolean allows(final Restriction restriction) {
        return this.mod(restriction.modId()).map(mod -> mod.allows(restriction)).orElse(false);
    }
}
