package main.walksy.lib.control.internal;

import main.walksy.lib.control.api.ClientInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;

final class Session {
    private final UUID uniqueId;
    private final RuleSetImpl own;
    private final Set<String> applied = new ConcurrentSkipListSet<>();
    private volatile @Nullable ClientInfo client;
    private volatile boolean listening;
    private volatile boolean helloReceived;
    private volatile @Nullable Snapshot sent;

    Session(final ModControlImpl control, final UUID uniqueId) {
        this.uniqueId = uniqueId;
        this.own = RuleSetImpl.personal(control, uniqueId);
    }

    UUID uniqueId() {
        return this.uniqueId;
    }

    RuleSetImpl own() {
        return this.own;
    }

    Set<String> applied() {
        return this.applied;
    }

    @Nullable ClientInfo client() {
        return this.client;
    }

    void client(final @Nullable ClientInfo client) {
        this.client = client;
    }

    boolean listening() {
        return this.listening;
    }

    void listening(final boolean listening) {
        this.listening = listening;
    }

    boolean helloReceived() {
        return this.helloReceived;
    }

    void helloReceived(final boolean helloReceived) {
        this.helloReceived = helloReceived;
    }

    @Nullable Snapshot sent() {
        return this.sent;
    }

    void sent(final @Nullable Snapshot sent) {
        this.sent = sent;
    }
}
