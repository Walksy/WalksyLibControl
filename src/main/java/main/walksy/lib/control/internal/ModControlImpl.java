package main.walksy.lib.control.internal;

import main.walksy.lib.control.api.ClientInfo;
import main.walksy.lib.control.api.ModControl;
import main.walksy.lib.control.api.PlayerControl;
import main.walksy.lib.control.api.ReportedMod;
import main.walksy.lib.control.api.Restriction;
import main.walksy.lib.control.api.RuleSet;
import main.walksy.lib.control.api.event.ClientModsReportedEvent;
import main.walksy.lib.control.api.event.PlayerRestrictionsChangeEvent;
import main.walksy.lib.control.platform.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModControlImpl implements ModControl {
    public static final String GLOBAL_ID = "global";
    public static final String BYPASS_PERMISSION = "wlcontrol.bypass";
    private static final Set<String> RESERVED_IDS = Set.of(GLOBAL_ID, "list", "create", "delete");
    private final Plugin plugin;
    private final TaskScheduler scheduler;
    private final RuleStorage storage;
    private final RuleSetImpl global;
    private final Map<String, RuleSetImpl> ruleSets = new ConcurrentSkipListMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Map<Restriction, String> liftNotices = new ConcurrentHashMap<>();
    private final AtomicBoolean allDirty = new AtomicBoolean();
    private final AtomicBoolean storageDirty = new AtomicBoolean();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private volatile boolean logClientReports;

    public ModControlImpl(final Plugin plugin, final TaskScheduler scheduler, final File rulesFile,
                          final boolean logClientReports) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.storage = new RuleStorage(rulesFile, plugin.getLogger());
        this.logClientReports = logClientReports;
        this.global = RuleSetImpl.global(this);
    }

    public static boolean isValidRuleSetId(final String id) {
        return Restriction.isValidId(id) && id.indexOf('.') < 0 && !RESERVED_IDS.contains(id.toLowerCase(Locale.ROOT));
    }

    public void logClientReports(final boolean logClientReports) {
        this.logClientReports = logClientReports;
    }

    @Override
    public RuleSet global() {
        return this.global;
    }

    @Override
    public RuleSet ruleSet(final String id) {
        return this.ruleSet(id, false);
    }

    public RuleSet ruleSet(final String id, final boolean persistent) {
        final String key = checkRuleSetId(id);
        return this.ruleSets.computeIfAbsent(key, ignored -> {
            if (persistent) {
                this.markStorageDirty();
            }
            return RuleSetImpl.named(this, key, persistent);
        });
    }

    @Override
    public Optional<RuleSet> findRuleSet(final String id) {
        if (id == null) {
            return Optional.empty();
        }
        final String key = id.toLowerCase(Locale.ROOT);
        return key.equals(GLOBAL_ID) ? Optional.of(this.global) : Optional.ofNullable(this.ruleSets.get(key));
    }

    @Override
    public Collection<RuleSet> ruleSets() {
        return List.copyOf(this.ruleSets.values());
    }

    @Override
    public boolean deleteRuleSet(final String id) {
        final RuleSetImpl removed = id == null ? null : this.ruleSets.remove(id.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        removed.markDeleted();
        for (final Session session : this.sessions.values()) {
            session.applied().remove(removed.id());
        }
        if (removed.isPersistent()) {
            this.markStorageDirty();
        }
        this.markAllDirty();
        return true;
    }

    @Override
    public PlayerControl player(final Player player) {
        return new PlayerControlImpl(this, player.getUniqueId());
    }

    @Override
    public int protocolVersion() {
        return ControlProtocol.PROTOCOL;
    }

    @Nullable Session session(final UUID uniqueId, final boolean create) {
        final Session existing = this.sessions.get(uniqueId);
        if (existing != null || !create) {
            return existing;
        }
        final Player player = Bukkit.getPlayer(uniqueId);
        if (player == null || !player.isOnline()) {
            return null;
        }
        return this.sessions.computeIfAbsent(uniqueId, id -> new Session(this, id));
    }

    void apply(final Player player, final RuleSetImpl ruleSet) {
        final Session session = this.session(player.getUniqueId(), true);
        if (session != null && session.applied().add(ruleSet.id())) {
            this.markDirty(player.getUniqueId());
        }
    }

    void remove(final Player player, final RuleSetImpl ruleSet) {
        final Session session = this.session(player.getUniqueId(), false);
        if (session != null && session.applied().remove(ruleSet.id())) {
            this.markDirty(player.getUniqueId());
        }
    }

    boolean isApplied(final Player player, final RuleSetImpl ruleSet) {
        final Session session = this.session(player.getUniqueId(), false);
        return session != null && session.applied().contains(ruleSet.id());
    }

    Collection<Player> appliedPlayers(final RuleSetImpl ruleSet) {
        final List<Player> players = new ArrayList<>();
        for (final Session session : this.sessions.values()) {
            if (session.applied().contains(ruleSet.id())) {
                final Player player = Bukkit.getPlayer(session.uniqueId());
                if (player != null) {
                    players.add(player);
                }
            }
        }
        return players;
    }

    @Nullable
    RuleSetImpl findNamed(final String id) {
        return id == null ? null : this.ruleSets.get(id.toLowerCase(Locale.ROOT));
    }

    void ruleSetChanged(final RuleSetImpl ruleSet) {
        if (ruleSet.kind() == RuleSetImpl.Kind.PERSONAL) {
            final UUID owner = ruleSet.owner();
            if (owner != null) {
                this.markDirty(owner);
            }
            return;
        }
        if (ruleSet.isPersistent() && !ruleSet.isDeleted()) {
            this.markStorageDirty();
        }
        this.markAllDirty();
    }

    Set<RuleSet> ruleSetsInForce(final Player player) {
        final Set<RuleSet> inForce = new LinkedHashSet<>();
        inForce.add(this.global);
        final Session session = this.session(player.getUniqueId(), false);
        for (final RuleSetImpl ruleSet : this.ruleSets.values()) {
            if ((session != null && session.applied().contains(ruleSet.id())) || ruleSet.boundTo(player)) {
                inForce.add(ruleSet);
            }
        }
        return inForce;
    }

    Snapshot compute(final Player player) {
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return Snapshot.EMPTY;
        }
        final Snapshot.Builder builder = Snapshot.builder();
        for (final RuleSet ruleSet : this.ruleSetsInForce(player)) {
            add(builder, ruleSet);
        }
        final Session session = this.session(player.getUniqueId(), false);
        if (session != null) {
            add(builder, session.own());
        }
        return builder.build();
    }

    private static void add(final Snapshot.Builder builder, final RuleSet ruleSet) {
        for (final Restriction restriction : ruleSet.restrictions()) {
            builder.add(restriction, ruleSet.reasonFor(restriction));
        }
    }

    void markDirty(final UUID uniqueId) {
        this.dirty.add(uniqueId);
        this.scheduleFlush();
    }

    void markAllDirty() {
        this.allDirty.set(true);
        this.scheduleFlush();
    }

    void markStorageDirty() {
        this.storageDirty.set(true);
        this.scheduleFlush();
    }

    private void scheduleFlush() {
        if (this.plugin.isEnabled() && this.flushScheduled.compareAndSet(false, true)) {
            this.scheduler.run(this::flush);
        }
    }

    public void flush() {
        this.flushScheduled.set(false);
        if (this.storageDirty.getAndSet(false)) {
            this.saveRules();
        }
        final Map<Restriction, String> lifts = new HashMap<>();
        for (final Restriction restriction : List.copyOf(this.liftNotices.keySet())) {
            final String reason = this.liftNotices.remove(restriction);
            if (reason != null) {
                lifts.put(restriction, reason);
            }
        }
        final Set<UUID> targets = new HashSet<>();
        final Iterator<UUID> drain = this.dirty.iterator();
        while (drain.hasNext()) {
            targets.add(drain.next());
            drain.remove();
        }
        final List<Player> players = new ArrayList<>();
        if (this.allDirty.getAndSet(false)) {
            players.addAll(Bukkit.getOnlinePlayers());
        } else {
            for (final UUID uniqueId : targets) {
                final Player player = Bukkit.getPlayer(uniqueId);
                if (player != null) {
                    players.add(player);
                }
            }
        }
        for (final Player player : players) {
            this.scheduler.runFor(player, () -> this.push(player, false, lifts));
        }
    }

    void noteLift(final Restriction restriction, final @Nullable String reason) {
        final String normalized = RuleSetImpl.normalizeReason(reason);
        if (!normalized.isEmpty()) {
            this.liftNotices.put(restriction, normalized);
        }
    }

    private void push(final Player player, final boolean force, final Map<Restriction, String> lifts) {
        final Session session = this.session(player.getUniqueId(), false);
        if (session == null || !session.listening() || !player.isOnline()) {
            return;
        }
        final Snapshot snapshot = this.compute(player);
        final Snapshot previous = session.sent();
        if (!force && snapshot.equals(previous)) {
            return;
        }
        final Map<Restriction, String> lifted = new LinkedHashMap<>();
        if (previous != null) {
            for (final Map.Entry<Restriction, String> lift : lifts.entrySet()) {
                if (previous.isRestricted(lift.getKey()) && !snapshot.isRestricted(lift.getKey())) {
                    lifted.put(lift.getKey(), lift.getValue());
                }
            }
        }
        player.sendPluginMessage(this.plugin, ControlProtocol.CONTROL_CHANNEL, snapshot.encode(lifted));
        session.sent(snapshot);
        final Snapshot before = previous == null ? Snapshot.EMPTY : previous;
        if (!before.equals(snapshot)) {
            Bukkit.getPluginManager().callEvent(new PlayerRestrictionsChangeEvent(player, before.restrictions(),
                    snapshot.restrictions()));
        }
    }

    public void onChannelRegistered(final Player player) {
        final Session session = this.session(player.getUniqueId(), true);
        if (session == null) {
            return;
        }
        session.listening(true);
        this.push(player, session.sent() == null, Map.of());
    }

    public void onChannelUnregistered(final Player player) {
        final Session session = this.session(player.getUniqueId(), false);
        if (session != null) {
            session.listening(false);
        }
    }

    public void onHello(final Player player, final byte[] message) {
        final Session session = this.session(player.getUniqueId(), true);
        if (session == null || session.helloReceived()) {
            return;
        }
        session.helloReceived(true);
        final ClientInfo client;
        try {
            client = ControlProtocol.decodeHello(message);
        } catch (final IllegalArgumentException e) {
            this.plugin.getLogger().warning("Unreadable walksylib:hello from " + player.getName() + ": " + e.getMessage());
            return;
        }
        session.client(client);
        if (this.logClientReports) {
            this.logReport(player, client);
        }
        Bukkit.getPluginManager().callEvent(new ClientModsReportedEvent(player, client));
    }

    public void onQuit(final Player player) {
        this.sessions.remove(player.getUniqueId());
        this.dirty.remove(player.getUniqueId());
    }

    public void adoptOnlinePlayers() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (player.getListeningPluginChannels().contains(ControlProtocol.CONTROL_CHANNEL)) {
                this.scheduler.runFor(player, () -> this.onChannelRegistered(player));
            }
        }
    }

    private void logReport(final Player player, final ClientInfo client) {
        if (client.mods().isEmpty()) {
            return;
        }
        final List<String> parts = new ArrayList<>();
        for (final ReportedMod mod : client.mods()) {
            final List<String> allows = new ArrayList<>();
            if (mod.wholeMod()) {
                allows.add("whole mod");
            }
            allows.addAll(mod.features());
            parts.add(mod.modId() + " (" + String.join(", ", allows) + ")");
        }
        this.plugin.getLogger().info(player.getName() + " lets the server switch off: " + String.join("; ", parts));
    }

    public Snapshot snapshotOf(final Player player) {
        return this.compute(player);
    }

    public @Nullable ClientInfo clientOf(final Player player) {
        final Session session = this.session(player.getUniqueId(), false);
        return session == null ? null : session.client();
    }

    public boolean isListening(final Player player) {
        final Session session = this.session(player.getUniqueId(), false);
        return session != null && session.listening();
    }

    public void loadRules() {
        final RuleStorage.Loaded loaded = this.storage.load();
        this.global.load(loaded.global().restrictions(), List.of(), null);
        final Set<String> seen = new HashSet<>();
        for (final RuleStorage.Stored stored : loaded.ruleSets()) {
            seen.add(stored.id());
            this.ruleSets.computeIfAbsent(stored.id(), id -> RuleSetImpl.named(this, id, true))
                    .load(stored.restrictions(), stored.worlds(), stored.permission());
        }
        for (final RuleSetImpl ruleSet : List.copyOf(this.ruleSets.values())) {
            if (ruleSet.isPersistent() && !seen.contains(ruleSet.id())) {
                this.deleteRuleSet(ruleSet.id());
            }
        }
        this.storageDirty.set(false);
        this.markAllDirty();
    }

    public void saveRules() {
        final List<RuleSetImpl> persistent = new ArrayList<>();
        for (final RuleSetImpl ruleSet : this.ruleSets.values()) {
            if (ruleSet.isPersistent()) {
                persistent.add(ruleSet);
            }
        }
        this.storage.save(this.global, persistent);
    }

    public void saveIfDirty() {
        if (this.storageDirty.getAndSet(false)) {
            this.saveRules();
        }
    }

    private static String checkRuleSetId(final String id) {
        if (id == null || !isValidRuleSetId(id)) {
            throw new IllegalArgumentException("Invalid rule set id \"" + id + "\": rule set ids are 1-64 characters of "
                    + "a-z, 0-9, '_' or '-', and cannot be global, list, create or delete");
        }
        return id.toLowerCase(Locale.ROOT);
    }
}
