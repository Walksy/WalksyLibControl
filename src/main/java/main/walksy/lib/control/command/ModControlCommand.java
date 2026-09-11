package main.walksy.lib.control.command;

import main.walksy.lib.control.api.ClientInfo;
import main.walksy.lib.control.api.PlayerControl;
import main.walksy.lib.control.api.ReportedMod;
import main.walksy.lib.control.api.Restriction;
import main.walksy.lib.control.api.RuleSet;
import main.walksy.lib.control.internal.Messages;
import main.walksy.lib.control.internal.ModControlImpl;
import main.walksy.lib.control.internal.Snapshot;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

public final class ModControlCommand {
    public static final String NAME = "wlcontrol";
    public static final List<String> ALIASES = List.of("wlc");
    static final String DISABLE = "disable";
    static final String ENABLE = "enable";
    static final String DISABLE_WITH_REASON = "disablewithreason";
    static final String ENABLE_WITH_REASON = "enablewithreason";
    static final List<String> EDITS = List.of(DISABLE, ENABLE, DISABLE_WITH_REASON, ENABLE_WITH_REASON);
    private static final Map<String, String> ROOT = Map.ofEntries(
            Map.entry("help", ""),
            Map.entry("status", ControlPermissions.STATUS),
            Map.entry(DISABLE, ControlPermissions.EDIT),
            Map.entry(ENABLE, ControlPermissions.EDIT),
            Map.entry(DISABLE_WITH_REASON, ControlPermissions.EDIT),
            Map.entry(ENABLE_WITH_REASON, ControlPermissions.EDIT),
            Map.entry("player", ControlPermissions.PLAYER),
            Map.entry("ruleset", ControlPermissions.RULE_SET),
            Map.entry("reload", ControlPermissions.RELOAD));
    private static final List<String> PLAYER_ACTIONS = List.of(DISABLE, ENABLE, DISABLE_WITH_REASON,
            ENABLE_WITH_REASON, "apply", "remove", "clear");
    private static final List<String> RULE_SET_ACTIONS = List.of("info", DISABLE, ENABLE, DISABLE_WITH_REASON,
            ENABLE_WITH_REASON, "clear", "world", "permission");
    private static final List<String> GLOBAL_ACTIONS = List.of("info", DISABLE, ENABLE, DISABLE_WITH_REASON,
            ENABLE_WITH_REASON, "clear");
    private static final List<String> SELECTORS = List.of("@a", "@p", "@r", "@s");
    private static final String EVERYTHING = "*";
    private static final String QUOTE = "\"";
    private final ModControlImpl control;
    private final Supplier<Messages> messages;
    private final Runnable reload;

    public ModControlCommand(final ModControlImpl control, final Supplier<Messages> messages, final Runnable reload) {
        this.control = control;
        this.messages = messages;
        this.reload = reload;
    }

    public void execute(final CommandSender sender, final String label, final String[] args) {
        if (!ControlPermissions.any(sender)) {
            this.send(sender, "no-permission");
            return;
        }
        if (args.length == 0) {
            this.help(sender, label);
            return;
        }
        final String sub = args[0].toLowerCase(Locale.ROOT);
        final String permission = ROOT.get(sub);
        if (permission == null) {
            this.help(sender, label);
            return;
        }
        if (!permission.isEmpty() && !ControlPermissions.has(sender, permission)) {
            this.send(sender, "no-permission");
            return;
        }
        switch (sub) {
            case "status" -> {
                if (args.length >= 2) {
                    this.statusOf(sender, args[1]);
                } else {
                    this.status(sender);
                }
            }
            case DISABLE, ENABLE, DISABLE_WITH_REASON, ENABLE_WITH_REASON ->
                    this.edit(sender, label + " " + sub, this.control.global(), sub, args, 1);
            case "player" -> this.player(sender, label, args);
            case "ruleset" -> this.ruleSet(sender, label, args);
            case "reload" -> {
                this.reload.run();
                this.send(sender, "reloaded");
            }
            default -> this.help(sender, label);
        }
    }

    private void edit(final CommandSender sender, final String usagePrefix, final RuleSet ruleSet, final String verb,
                      final String[] args, final int from) {
        final boolean enable = isEnable(verb);
        final Target target = this.target(sender, usagePrefix, args, from, enable, isWithReason(verb));
        if (target == null) {
            return;
        }
        final String where = this.where(ruleSet);
        final String told = target.reason() == null ? "" : this.text("told", "reason", target.reason());
        if (target.everything()) {
            for (final Restriction restriction : ruleSet.restrictions()) {
                if (restriction.modId().equals(target.modId())) {
                    ruleSet.enable(restriction, target.reason());
                }
            }
            this.send(sender, "everything-back-on", "mod", target.modId(), "where", where, "told", told);
            return;
        }
        final Restriction restriction = target.restriction();
        final String described = this.describe(restriction);
        if (!enable) {
            final boolean alreadyOff = ruleSet.contains(restriction);
            if (target.reason() == null) {
                if (alreadyOff) {
                    final String own = ruleSet.reasonFor(restriction);
                    this.send(sender, "already-off", "target", described, "where", where,
                            "reason", own.isEmpty() ? "" : this.text("current-reason", "reason", own));
                    return;
                }
                ruleSet.disable(restriction);
                this.send(sender, "switched-off", "target", described, "where", where, "told", "");
                this.obeyedBy(sender, restriction);
                return;
            }
            ruleSet.disable(restriction, target.reason());
            if (alreadyOff) {
                this.send(sender, "reason-changed", "reason", target.reason(), "target", described, "where", where);
                return;
            }
            this.send(sender, "switched-off", "target", described, "where", where, "told", told);
            this.obeyedBy(sender, restriction);
            return;
        }
        if (!ruleSet.contains(restriction)) {
            final boolean featuresOff = restriction.isWholeMod() && this.hasFeaturesOff(ruleSet, restriction.modId());
            this.send(sender, "not-off", "target", described, "where", where,
                    "hint", featuresOff ? this.text("features-still-off", "mod", restriction.modId()) : "");
            return;
        }
        ruleSet.enable(restriction, target.reason());
        this.send(sender, "switched-on", "target", described, "where", where, "told", told);
    }

    private static boolean isEnable(final String verb) {
        return verb.equalsIgnoreCase(ENABLE) || verb.equalsIgnoreCase(ENABLE_WITH_REASON);
    }

    private static boolean isWithReason(final String verb) {
        return verb.equalsIgnoreCase(DISABLE_WITH_REASON) || verb.equalsIgnoreCase(ENABLE_WITH_REASON);
    }

    private static boolean isEdit(final String verb) {
        return EDITS.contains(verb.toLowerCase(Locale.ROOT));
    }

    private boolean hasFeaturesOff(final RuleSet ruleSet, final String modId) {
        for (final Restriction restriction : ruleSet.restrictions()) {
            if (restriction.modId().equals(modId) && !restriction.isWholeMod()) {
                return true;
            }
        }
        return false;
    }

    private void obeyedBy(final CommandSender sender, final Restriction restriction) {
        int clients = 0;
        int obeying = 0;
        for (final Player player : Bukkit.getOnlinePlayers()) {
            final ClientInfo client = this.control.clientOf(player);
            if (client == null) {
                continue;
            }
            clients++;
            if (client.allows(restriction)) {
                obeying++;
            }
        }
        if (clients > 0) {
            this.send(sender, "obeyed-by", "obeying", String.valueOf(obeying), "clients", String.valueOf(clients));
        }
    }

    private void player(final CommandSender sender, final String label, final String[] args) {
        final String usage = label + " player <player|@selector> " + String.join("|", PLAYER_ACTIONS) + " ...";
        if (args.length < 3) {
            this.send(sender, "usage", "usage", usage);
            return;
        }
        final List<Player> targets = this.resolve(sender, args[1]);
        if (targets.isEmpty()) {
            return;
        }
        final String action = args[2].toLowerCase(Locale.ROOT);
        final String who = targets.size() == 1
                ? targets.get(0).getName()
                : this.text("who-players", "count", String.valueOf(targets.size()));
        switch (action) {
            case DISABLE, ENABLE, DISABLE_WITH_REASON, ENABLE_WITH_REASON -> {
                final boolean enable = isEnable(action);
                final Target target = this.target(sender, label + " player " + args[1] + " " + action, args, 3,
                        enable, isWithReason(action));
                if (target == null) {
                    return;
                }
                final String told = target.reason() == null ? "" : this.text("told-player", "reason", target.reason());
                if (target.everything()) {
                    for (final Player player : targets) {
                        final PlayerControl control = this.control.player(player);
                        for (final Restriction restriction : control.ownRestrictions()) {
                            if (restriction.modId().equals(target.modId())) {
                                control.enable(restriction, target.reason());
                            }
                        }
                    }
                    this.send(sender, "player-cleared-mod", "who", who, "mod", target.modId(), "told", told);
                    return;
                }
                final Restriction restriction = target.restriction();
                for (final Player player : targets) {
                    final PlayerControl control = this.control.player(player);
                    if (enable) {
                        control.enable(restriction, target.reason());
                    } else if (target.reason() != null) {
                        control.disable(restriction, target.reason());
                    } else {
                        control.disable(restriction);
                    }
                }
                this.send(sender, enable ? "player-switched-on" : "player-switched-off",
                        "target", this.describe(restriction), "who", who, "told", told);
                if (enable) {
                    this.send(sender, "player-enable-note", "label", label);
                } else {
                    this.obeyedBy(sender, restriction);
                }
            }
            case "apply", "remove" -> {
                if (args.length < 4) {
                    this.send(sender, "usage", "usage", label + " player " + args[1] + " " + action + " <ruleset>");
                    return;
                }
                final RuleSet ruleSet = this.namedRuleSet(sender, label, args[3]);
                if (ruleSet == null) {
                    return;
                }
                for (final Player player : targets) {
                    if (action.equals("apply")) {
                        ruleSet.applyTo(player);
                    } else {
                        ruleSet.removeFrom(player);
                    }
                }
                this.send(sender, action.equals("apply") ? "player-applied" : "player-removed",
                        "rule-set", ruleSet.id(), "who", who);
            }
            case "clear" -> {
                targets.forEach(player -> this.control.player(player).clear());
                this.send(sender, "player-cleared", "who", who);
            }
            default -> this.send(sender, "usage", "usage", usage);
        }
    }

    private void ruleSet(final CommandSender sender, final String label, final String[] args) {
        if (args.length < 2) {
            this.send(sender, "usage", "usage", label + " ruleset list | create <id> | delete <id> | <id> ...");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                this.listRuleSets(sender);
                return;
            }
            case "create" -> {
                if (args.length < 3) {
                    this.send(sender, "usage", "usage", label + " ruleset create <id>");
                    return;
                }
                if (!ModControlImpl.isValidRuleSetId(args[2])) {
                    this.send(sender, "rule-set-invalid-id", "id", args[2]);
                    return;
                }
                final String id = args[2].toLowerCase(Locale.ROOT);
                if (this.control.findRuleSet(id).isPresent()) {
                    this.send(sender, "rule-set-exists", "id", id);
                    return;
                }
                this.control.ruleSet(id, true);
                this.send(sender, "rule-set-created", "id", id, "label", label);
                return;
            }
            case "delete" -> {
                if (args.length < 3) {
                    this.send(sender, "usage", "usage", label + " ruleset delete <id>");
                    return;
                }
                if (args[2].equalsIgnoreCase(ModControlImpl.GLOBAL_ID)) {
                    this.send(sender, "rule-set-global-undeletable", "label", label);
                    return;
                }
                if (this.control.deleteRuleSet(args[2])) {
                    this.send(sender, "rule-set-deleted", "id", args[2].toLowerCase(Locale.ROOT));
                } else {
                    this.send(sender, "rule-set-missing", "id", args[2], "label", label);
                }
                return;
            }
            default -> {
            }
        }
        final Optional<RuleSet> found = this.control.findRuleSet(args[1]);
        if (found.isEmpty()) {
            this.send(sender, "rule-set-missing", "id", args[1], "label", label);
            return;
        }
        final RuleSet ruleSet = found.get();
        final String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "info";
        try {
            switch (action) {
                case "info" -> this.info(sender, ruleSet);
                case DISABLE, ENABLE, DISABLE_WITH_REASON, ENABLE_WITH_REASON ->
                        this.edit(sender, label + " ruleset " + ruleSet.id() + " " + action, ruleSet, action, args, 3);
                case "clear" -> {
                    ruleSet.clear();
                    this.send(sender, "rule-set-cleared", "id", ruleSet.id());
                }
                case "world" -> this.world(sender, label, ruleSet, args);
                case "permission" -> {
                    if (args.length < 4) {
                        this.send(sender, "usage", "usage", label + " ruleset " + ruleSet.id() + " permission <node|none>");
                        return;
                    }
                    final boolean none = args[3].equalsIgnoreCase("none");
                    ruleSet.bindPermission(none ? null : args[3]);
                    if (none) {
                        this.send(sender, "rule-set-permission-unbound", "id", ruleSet.id());
                    } else {
                        this.send(sender, "rule-set-permission-bound", "id", ruleSet.id(), "permission", args[3]);
                    }
                }
                default -> this.send(sender, "usage", "usage", label + " ruleset " + ruleSet.id() + " "
                        + String.join("|", RULE_SET_ACTIONS) + " ...");
            }
        } catch (final UnsupportedOperationException e) {
            this.send(sender, "rule-set-global-unbindable");
        }
    }

    private void world(final CommandSender sender, final String label, final RuleSet ruleSet, final String[] args) {
        if (args.length < 5 || !(args[3].equalsIgnoreCase("add") || args[3].equalsIgnoreCase("remove"))) {
            this.send(sender, "usage", "usage", label + " ruleset " + ruleSet.id() + " world add|remove <world>");
            return;
        }
        final String world = args[4];
        if (args[3].equalsIgnoreCase("add")) {
            ruleSet.bindWorld(world);
            this.send(sender, "rule-set-world-added", "id", ruleSet.id(), "world", world);
            if (Bukkit.getWorld(world) == null) {
                this.send(sender, "rule-set-world-not-loaded", "world", world);
            }
        } else {
            ruleSet.unbindWorld(world);
            this.send(sender, "rule-set-world-removed", "id", ruleSet.id(), "world", world);
        }
    }

    private void status(final CommandSender sender) {
        this.send(sender, "status-global-header");
        this.restrictionLines(sender, this.control.global());
        this.send(sender, "status-rule-sets-header");
        final Collection<RuleSet> ruleSets = this.control.ruleSets();
        if (ruleSets.isEmpty()) {
            this.send(sender, "status-none");
        }
        for (final RuleSet ruleSet : ruleSets) {
            this.send(sender, "status-rule-set-line", "rule-set", ruleSet.id(), "summary", this.summary(ruleSet));
        }
        this.send(sender, "status-players-header");
        int listening = 0;
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (!this.control.isListening(player)) {
                continue;
            }
            listening++;
            final ClientInfo client = this.control.clientOf(player);
            final int restricted = this.control.player(player).effective().size();
            this.send(sender, "status-player-line", "player", player.getName(),
                    "mods", client == null
                            ? this.text("status-waiting")
                            : this.text("status-mod-count", "count", String.valueOf(client.mods().size())),
                    "off", restricted == 0 ? "" : this.text("status-off-count", "count", String.valueOf(restricted)));
        }
        if (listening == 0) {
            this.send(sender, "status-nobody");
        }
    }

    private void statusOf(final CommandSender sender, final String target) {
        final List<Player> players = this.resolve(sender, target);
        if (players.isEmpty()) {
            return;
        }
        final Player player = players.get(0);
        final PlayerControl control = this.control.player(player);
        final ClientInfo client = control.client().orElse(null);
        this.send(sender, "status-player-header", "player", player.getName());
        if (!control.hasWalksyLib()) {
            this.send(sender, "status-no-walksylib");
        } else if (client == null) {
            this.send(sender, "status-waiting-report");
        } else {
            for (final ReportedMod mod : client.mods()) {
                final List<String> allows = new ArrayList<>();
                if (mod.wholeMod()) {
                    allows.add(this.text("status-whole-mod"));
                }
                allows.addAll(mod.features());
                this.send(sender, "status-mod-line", "name", mod.displayName(), "mod", mod.modId(),
                        "allows", String.join(", ", allows));
            }
        }
        if (control.isBypassing()) {
            this.send(sender, "status-bypass", "permission", ModControlImpl.BYPASS_PERMISSION);
            return;
        }
        final List<String> inForce = new ArrayList<>();
        for (final RuleSet ruleSet : control.ruleSetsInForce()) {
            if (!ruleSet.isGlobal()) {
                inForce.add(ruleSet.id() + " (" + this.why(ruleSet, player) + ")");
            }
        }
        this.send(sender, "status-rule-sets", "rule-sets",
                inForce.isEmpty() ? this.text("status-global-only") : "global, " + String.join(", ", inForce));
        if (!control.ownRestrictions().isEmpty()) {
            this.send(sender, "status-own", "restrictions", join(control.ownRestrictions()));
        }
        final Set<Restriction> effective = control.effective();
        if (effective.isEmpty()) {
            this.send(sender, "status-nothing-off");
            return;
        }
        this.send(sender, "status-off-header");
        final Snapshot snapshot = this.control.snapshotOf(player);
        for (final Restriction restriction : effective) {
            final String reason = reasonIn(snapshot, restriction);
            final boolean ignored = client != null && !client.allows(restriction);
            this.send(sender, ignored ? "status-off-line-ignored" : "status-off-line", "target", words(restriction),
                    "reason", reason.isEmpty() ? "" : this.text("status-reason", "reason", reason));
        }
    }

    private static String reasonIn(final Snapshot snapshot, final Restriction restriction) {
        final Snapshot.ModEntry entry = snapshot.mods().get(restriction.modId());
        if (entry == null) {
            return "";
        }
        if (restriction.isWholeMod()) {
            return entry.modReason();
        }
        final String own = entry.features().get(restriction.feature());
        return own == null ? "" : own;
    }

    private String why(final RuleSet ruleSet, final Player player) {
        final List<String> reasons = new ArrayList<>();
        if (ruleSet.isAppliedTo(player)) {
            reasons.add(this.text("status-why-applied"));
        }
        if (ruleSet.worlds().contains(player.getWorld().getName())) {
            reasons.add(this.text("status-why-world"));
        }
        final String permission = ruleSet.permission();
        if (permission != null && player.hasPermission(permission)) {
            reasons.add(this.text("status-why-permission"));
        }
        return String.join(", ", reasons);
    }

    private void info(final CommandSender sender, final RuleSet ruleSet) {
        if (ruleSet.isGlobal()) {
            this.send(sender, "info-global-header");
        } else {
            this.send(sender, "info-header", "rule-set", ruleSet.id());
        }
        this.restrictionLines(sender, ruleSet);
        if (ruleSet.isGlobal()) {
            return;
        }
        final List<String> applied = new ArrayList<>();
        for (final Player player : ruleSet.appliedPlayers()) {
            applied.add(player.getName());
        }
        this.send(sender, "info-worlds", "value",
                ruleSet.worlds().isEmpty() ? this.text("info-none") : String.join(", ", ruleSet.worlds()));
        this.send(sender, "info-permission", "value",
                ruleSet.permission() == null ? this.text("info-none") : ruleSet.permission());
        this.send(sender, "info-applied", "value",
                applied.isEmpty() ? this.text("info-nobody") : String.join(", ", applied));
        this.send(sender, "info-saved", "value",
                this.text(ruleSet.isPersistent() ? "info-saved-yes" : "info-saved-no"));
    }

    private void restrictionLines(final CommandSender sender, final RuleSet ruleSet) {
        if (ruleSet.restrictions().isEmpty()) {
            this.send(sender, "info-nothing");
        } else {
            for (final Restriction restriction : ruleSet.restrictions()) {
                final String reason = ruleSet.reasonFor(restriction);
                this.send(sender, "info-restriction", "target", words(restriction),
                        "reason", reason.isEmpty() ? "" : this.text("status-reason", "reason", reason));
            }
        }
    }

    private void listRuleSets(final CommandSender sender) {
        this.send(sender, "list-header");
        this.send(sender, "list-line", "rule-set", ModControlImpl.GLOBAL_ID, "summary", this.summary(this.control.global()));
        for (final RuleSet ruleSet : this.control.ruleSets()) {
            this.send(sender, "list-line", "rule-set", ruleSet.id(), "summary", this.summary(ruleSet));
        }
    }

    private String summary(final RuleSet ruleSet) {
        final List<String> parts = new ArrayList<>();
        parts.add(ruleSet.restrictions().isEmpty() ? this.text("summary-nothing-off") : join(ruleSet.restrictions()));
        if (!ruleSet.worlds().isEmpty()) {
            parts.add(this.text("summary-worlds", "worlds", String.join(", ", ruleSet.worlds())));
        }
        if (ruleSet.permission() != null) {
            parts.add(this.text("summary-permission", "permission", ruleSet.permission()));
        }
        final int applied = ruleSet.isGlobal() ? 0 : ruleSet.appliedPlayers().size();
        if (applied > 0) {
            parts.add(this.text("summary-applied", "count", String.valueOf(applied)));
        }
        if (!ruleSet.isGlobal() && !ruleSet.isPersistent()) {
            parts.add(this.text("summary-not-saved"));
        }
        return String.join("; ", parts);
    }

    private void help(final CommandSender sender, final String label) {
        this.send(sender, "help-header", "label", label);
        this.send(sender, "help-intro");
        if (ControlPermissions.has(sender, ControlPermissions.EDIT)) {
            for (final String key : List.of("help-disable", "help-disablewithreason", "help-enable",
                    "help-enablewithreason")) {
                this.send(sender, key, "label", label);
            }
        }
        if (ControlPermissions.has(sender, ControlPermissions.STATUS)) {
            this.send(sender, "help-status", "label", label);
        }
        if (ControlPermissions.has(sender, ControlPermissions.PLAYER)) {
            for (final String key : List.of("help-player-edit", "help-player-apply", "help-player-clear")) {
                this.send(sender, key, "label", label);
            }
        }
        if (ControlPermissions.has(sender, ControlPermissions.RULE_SET)) {
            for (final String key : List.of("help-ruleset", "help-ruleset-edit", "help-ruleset-more")) {
                this.send(sender, key, "label", label);
            }
        }
        if (ControlPermissions.has(sender, ControlPermissions.RELOAD)) {
            this.send(sender, "help-reload", "label", label);
        }
    }

    private List<Player> resolve(final CommandSender sender, final String target) {
        final List<Player> players = new ArrayList<>();
        if (target.startsWith("@")) {
            try {
                for (final Entity entity : Bukkit.selectEntities(sender, target)) {
                    if (entity instanceof final Player player) {
                        players.add(player);
                    }
                }
            } catch (final IllegalArgumentException e) {
                this.send(sender, "bad-selector", "selector", target, "error", String.valueOf(e.getMessage()));
                return players;
            } catch (final UnsupportedOperationException e) {
                this.send(sender, "selectors-unsupported");
                return players;
            }
            if (players.isEmpty()) {
                this.send(sender, "no-match", "selector", target);
            }
            return players;
        }
        final Player player = Bukkit.getPlayerExact(target);
        if (player == null) {
            this.send(sender, "not-online", "player", target);
        } else {
            players.add(player);
        }
        return players;
    }

    private @Nullable RuleSet namedRuleSet(final CommandSender sender, final String label, final String id) {
        if (id.equalsIgnoreCase(ModControlImpl.GLOBAL_ID)) {
            this.send(sender, "global-always-applies");
            return null;
        }
        final Optional<RuleSet> found = this.control.findRuleSet(id);
        if (found.isEmpty()) {
            this.send(sender, "rule-set-missing", "id", id, "label", label);
            return null;
        }
        return found.get();
    }

    private record Target(String modId, @Nullable String feature, @Nullable String reason) {

        boolean everything() {
            return EVERYTHING.equals(this.feature);
        }

        Restriction restriction() {
            return this.feature == null ? Restriction.mod(this.modId) : Restriction.feature(this.modId, this.feature);
        }
    }

    private @Nullable Target target(final CommandSender sender, final String usagePrefix, final String[] args,
                                    final int from, final boolean enable, final boolean withReason) {
        final String usage = usagePrefix + (enable ? " <modId> [feature|*]" : " <modId> [feature]")
                + (withReason ? " \"<reason>\"" : "");
        int end = args.length;
        String reason = null;
        if (withReason) {
            end = -1;
            for (int i = from; i < args.length; i++) {
                if (args[i].startsWith(QUOTE)) {
                    end = i;
                    break;
                }
            }
            if (end < 0) {
                this.send(sender, "usage", "usage", usage);
                this.send(sender, "quote-hint");
                return null;
            }
            reason = unquote(join(args, end));
            if (reason.isEmpty()) {
                this.send(sender, "empty-reason", "verb", enable ? ENABLE : DISABLE);
                return null;
            }
        }
        final int words = end - from;
        if (words < 1 || words > 2) {
            this.send(sender, "usage", "usage", usage);
            return null;
        }
        String modId = args[from];
        String feature = words == 2 ? args[from + 1] : null;
        final int colon = modId.indexOf(':');
        if (feature == null && colon >= 0) {
            feature = modId.substring(colon + 1);
            modId = modId.substring(0, colon);
        }
        if (EVERYTHING.equals(feature)) {
            if (!enable) {
                this.send(sender, "usage", "usage", usage);
                return null;
            }
            if (!Restriction.isValidId(modId)) {
                this.send(sender, "invalid-mod-id", "mod", modId);
                return null;
            }
            return new Target(modId.toLowerCase(Locale.ROOT), EVERYTHING, reason);
        }
        try {
            final Restriction parsed = feature == null ? Restriction.mod(modId) : Restriction.feature(modId, feature);
            return new Target(parsed.modId(), parsed.feature(), reason);
        } catch (final IllegalArgumentException e) {
            this.send(sender, "invalid-id", "error", String.valueOf(e.getMessage()));
            return null;
        }
    }

    public List<String> complete(final CommandSender sender, final String label, final String[] args) {
        if (!ControlPermissions.any(sender) || args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            final List<String> allowed = new ArrayList<>();
            for (final Map.Entry<String, String> sub : ROOT.entrySet()) {
                if (sub.getValue().isEmpty() || ControlPermissions.has(sender, sub.getValue())) {
                    allowed.add(sub.getKey());
                }
            }
            return matching(new TreeSet<>(allowed), args[0]);
        }
        final String sub = args[0].toLowerCase(Locale.ROOT);
        final String permission = ROOT.get(sub);
        if (permission == null || (!permission.isEmpty() && !ControlPermissions.has(sender, permission))) {
            return List.of();
        }
        return switch (sub) {
            case "status" -> args.length == 2 ? matching(this.playerNames(), args[1]) : List.of();
            case DISABLE, ENABLE, DISABLE_WITH_REASON, ENABLE_WITH_REASON ->
                    this.completeTarget(args, 1, sub, this.control.global().restrictions());
            case "player" -> this.completePlayer(args);
            case "ruleset" -> this.completeRuleSet(args);
            default -> List.of();
        };
    }

    private List<String> completeTarget(final String[] args, final int from, final String verb,
                                        final Set<Restriction> current) {
        final boolean enable = isEnable(verb);
        final boolean withReason = isWithReason(verb);
        final Set<Restriction> pool = enable ? current : Set.of();
        if (args.length == from + 1) {
            final Set<String> mods = new TreeSet<>();
            for (final Restriction restriction : pool) {
                mods.add(restriction.modId());
            }
            return matching(mods, args[from]);
        }
        if (args.length == from + 2) {
            final String modId = args[from].toLowerCase(Locale.ROOT);
            final Set<String> features = new TreeSet<>();
            for (final Restriction restriction : pool) {
                if (restriction.modId().equals(modId) && !restriction.isWholeMod()) {
                    features.add(restriction.feature());
                }
            }
            if (enable && !features.isEmpty()) {
                features.add(EVERYTHING);
            }
            if (withReason) {
                features.add(QUOTE);
            }
            return matching(features, args[from + 1]);
        }
        if (withReason && args.length == from + 3 && !args[from + 1].startsWith(QUOTE)) {
            return matching(List.of(QUOTE), args[from + 2]);
        }
        return List.of();
    }

    private List<String> completePlayer(final String[] args) {
        if (args.length == 2) {
            final List<String> names = this.playerNames();
            names.addAll(SELECTORS);
            return matching(names, args[1]);
        }
        if (args.length == 3) {
            return matching(PLAYER_ACTIONS, args[2]);
        }
        final String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("apply") || action.equals("remove")) {
            return args.length == 4 ? matching(this.ruleSetIds(false), args[3]) : List.of();
        }
        if (isEdit(action)) {
            final Player player = Bukkit.getPlayerExact(args[1]);
            final Set<Restriction> own = player == null ? Set.of() : this.control.player(player).ownRestrictions();
            return this.completeTarget(args, 3, action, own);
        }
        return List.of();
    }

    private List<String> completeRuleSet(final String[] args) {
        if (args.length == 2) {
            final List<String> options = new ArrayList<>(List.of("list", "create", "delete"));
            options.addAll(this.ruleSetIds(true));
            return matching(options, args[1]);
        }
        final String first = args[1].toLowerCase(Locale.ROOT);
        if (first.equals("create") || first.equals("list")) {
            return List.of();
        }
        if (first.equals("delete")) {
            return args.length == 3 ? matching(this.ruleSetIds(false), args[2]) : List.of();
        }
        final Optional<RuleSet> found = this.control.findRuleSet(first);
        if (found.isEmpty()) {
            return List.of();
        }
        final RuleSet ruleSet = found.get();
        if (args.length == 3) {
            return matching(ruleSet.isGlobal() ? GLOBAL_ACTIONS : RULE_SET_ACTIONS, args[2]);
        }
        final String action = args[2].toLowerCase(Locale.ROOT);
        if (isEdit(action)) {
            return this.completeTarget(args, 3, action, ruleSet.restrictions());
        }
        if (args.length == 4) {
            return switch (action) {
                case "world" -> matching(List.of("add", "remove"), args[3]);
                case "permission" -> matching(ruleSet.permission() == null
                        ? List.of("none")
                        : List.of("none", ruleSet.permission()), args[3]);
                default -> List.of();
            };
        }
        if (args.length == 5 && action.equals("world")) {
            if (args[3].equalsIgnoreCase("remove")) {
                return matching(ruleSet.worlds(), args[4]);
            }
            final List<String> worlds = new ArrayList<>();
            for (final World world : Bukkit.getWorlds()) {
                worlds.add(world.getName());
            }
            return matching(worlds, args[4]);
        }
        return List.of();
    }

    private List<String> ruleSetIds(final boolean withGlobal) {
        final List<String> ids = new ArrayList<>();
        if (withGlobal) {
            ids.add(ModControlImpl.GLOBAL_ID);
        }
        for (final RuleSet ruleSet : this.control.ruleSets()) {
            ids.add(ruleSet.id());
        }
        return ids;
    }

    private List<String> playerNames() {
        final List<String> names = new ArrayList<>();
        for (final Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private static List<String> matching(final Collection<String> options, final String prefix) {
        final String lower = prefix.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<>();
        for (final String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }

    private String where(final RuleSet ruleSet) {
        return ruleSet.isGlobal() ? this.text("where-global") : this.text("where-rule-set", "rule-set", ruleSet.id());
    }

    private String describe(final Restriction restriction) {
        return restriction.isWholeMod()
                ? this.text("target-mod", "mod", restriction.modId())
                : this.text("target-feature", "feature", restriction.feature(), "mod", restriction.modId());
    }

    private static String words(final Restriction restriction) {
        return restriction.isWholeMod() ? restriction.modId() : restriction.modId() + " " + restriction.feature();
    }

    private static String join(final Collection<Restriction> restrictions) {
        final List<String> names = new ArrayList<>();
        for (final Restriction restriction : restrictions) {
            names.add(words(restriction));
        }
        return String.join(", ", names);
    }

    private static String join(final String[] args, final int from) {
        return from >= args.length ? "" : String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private static String unquote(final String text) {
        String inner = text.strip();
        if (inner.startsWith(QUOTE)) {
            inner = inner.substring(1);
        }
        if (inner.endsWith(QUOTE)) {
            inner = inner.substring(0, inner.length() - 1);
        }
        return inner.strip();
    }

    private String text(final String key, final String... pairs) {
        return this.messages.get().get(key, pairs);
    }

    private void send(final CommandSender sender, final String key, final String... pairs) {
        this.messages.get().send(sender, key, pairs);
    }
}
