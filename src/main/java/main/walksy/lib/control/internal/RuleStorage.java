package main.walksy.lib.control.internal;

import main.walksy.lib.control.api.Restriction;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class RuleStorage {
    private static final String GLOBAL = "global";
    private static final String RULE_SETS = "rule-sets";
    private static final String REASON = "reason";
    private static final String ID = "id";
    private static final String DISABLE = "disable";
    private static final String WORLDS = "worlds";
    private static final String PERMISSION = "permission";
    private static final List<String> HEADER = List.of(
            "Entries are <modId> or <modId>:<feature> (or {id: ..., reason: ...}); edit with /wlcontrol");
    private final File file;
    private final Logger logger;

    RuleStorage(final File file, final Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    record Stored(String id, Map<Restriction, String> restrictions, List<String> worlds, @Nullable String permission) {
    }

    record Loaded(Stored global, List<Stored> ruleSets) {
    }

    Loaded load() {
        if (!this.file.exists()) {
            return new Loaded(new Stored(GLOBAL, Map.of(), List.of(), null), List.of());
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
        final ConfigurationSection globalSection = yaml.getConfigurationSection(GLOBAL);
        final Stored global = globalSection == null
                ? new Stored(GLOBAL, Map.of(), List.of(), null)
                : this.read(GLOBAL, globalSection);
        final List<Stored> ruleSets = new ArrayList<>();
        final ConfigurationSection root = yaml.getConfigurationSection(RULE_SETS);
        if (root != null) {
            for (final String id : root.getKeys(false)) {
                final ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                if (!ModControlImpl.isValidRuleSetId(id)) {
                    this.logger.warning("rules.yml: skipping rule set \"" + id + "\" - rule set ids are 1-64 characters "
                            + "of a-z, 0-9, '_' or '-', and cannot be global, list, create or delete");
                    continue;
                }
                ruleSets.add(this.read(id, section));
            }
        }
        return new Loaded(global, ruleSets);
    }

    private Stored read(final String id, final ConfigurationSection section) {
        final Map<Restriction, String> restrictions = new LinkedHashMap<>();
        final List<?> entries = section.getList(DISABLE, List.of());
        for (final Object entry : entries) {
            final Object text;
            final Object reason;
            if (entry instanceof final Map<?, ?> map) {
                text = map.get(ID);
                reason = map.get(REASON);
            } else if (entry instanceof final ConfigurationSection nested) {
                text = nested.get(ID);
                reason = nested.get(REASON);
            } else {
                text = entry;
                reason = null;
            }
            if (text == null) {
                this.logger.warning("rules.yml: skipping an entry with no 'id' in " + id);
                continue;
            }
            try {
                restrictions.put(Restriction.parse(String.valueOf(text)), reason == null ? "" : String.valueOf(reason));
            } catch (final IllegalArgumentException e) {
                this.logger.warning("rules.yml: skipping \"" + text + "\" in " + id + " - " + e.getMessage());
            }
        }
        return new Stored(id.toLowerCase(Locale.ROOT), restrictions, section.getStringList(WORLDS),
                section.getString(PERMISSION, null));
    }

    void save(final RuleSetImpl global, final Collection<RuleSetImpl> ruleSets) {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(HEADER);
        this.write(yaml.createSection(GLOBAL), global, false);
        final ConfigurationSection root = yaml.createSection(RULE_SETS);
        for (final RuleSetImpl ruleSet : ruleSets) {
            this.write(root.createSection(ruleSet.id()), ruleSet, true);
        }
        try {
            yaml.save(this.file);
        } catch (final IOException e) {
            this.logger.log(Level.SEVERE, "Could not save " + this.file, e);
        }
    }

    private void write(final ConfigurationSection section, final RuleSetImpl ruleSet, final boolean bindings) {
        final List<Object> disable = new ArrayList<>();
        for (final Restriction restriction : ruleSet.restrictions()) {
            final String reason = ruleSet.reasonFor(restriction);
            if (reason.isEmpty()) {
                disable.add(restriction.toString());
            } else {
                final Map<String, String> entry = new LinkedHashMap<>();
                entry.put(ID, restriction.toString());
                entry.put(REASON, reason);
                disable.add(entry);
            }
        }
        section.set(DISABLE, disable);
        if (bindings) {
            section.set(WORLDS, new ArrayList<>(ruleSet.worlds()));
            section.set(PERMISSION, ruleSet.permission() == null ? "" : ruleSet.permission());
        }
    }
}
