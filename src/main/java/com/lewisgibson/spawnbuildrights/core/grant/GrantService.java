package com.lewisgibson.spawnbuildrights.core.grant;

import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The by-hand decisions and the earned record, applied to the in-memory registry at once and
 * written through to the store afterwards.
 *
 * <p>The registry update is what the next block event sees, so a command takes effect the moment it
 * returns; the store write is handed to {@code writer} (the plugin's storage thread) so no command
 * and no event ever waits on a database. Reading history is the one synchronous call, made from an
 * admin command a few times a day.
 */
public final class GrantService {

    /** The actor recorded on an earned event, which no person made. */
    public static final String PLAYTIME_ACTOR = "playtime";

    private final GrantRegistry registry;
    private final GrantStore store;
    private final Consumer<Runnable> writer;
    private final Clock clock;
    // Set once, when the plugin switches stores, and read without the lock on the earned fast path.
    private volatile GrantService successor;

    /**
     * @param registry the in-memory copy the hot path reads
     * @param store the durable store behind it
     * @param writer runs a store write off the calling thread
     * @param clock the clock for timestamps
     */
    public GrantService(
            GrantRegistry registry, GrantStore store, Consumer<Runnable> writer, Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.store = Objects.requireNonNull(store, "store");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @return the registry this service keeps in step with the store
     */
    public GrantRegistry registry() {
        return registry;
    }

    /**
     * Hands this service over to the one for a new store, so a decision made through this service
     * after the switch, by a thread that read it just before, lands in the new store instead of
     * being lost with the old one. Synchronised with every decision: one already being made
     * finishes, and has its write queued, before the switch; every one after it is passed on.
     *
     * @param next builds the service for the new store; if it throws, nothing is handed over
     * @return the new service
     */
    public synchronized GrantService handOver(Supplier<GrantService> next) {
        if (successor != null) {
            return successor.handOver(next);
        }
        GrantService built = Objects.requireNonNull(next.get(), "next");
        successor = built;
        return built;
    }

    /**
     * Grants build access by hand, whatever the player's playtime.
     *
     * @return the override recorded
     */
    public GrantOverride grant(UUID player, String playerName, String actor, String reason) {
        return override(player, playerName, OverrideState.GRANTED, actor, reason);
    }

    /**
     * Revokes build access by hand, however long the player plays.
     *
     * @return the override recorded
     */
    public GrantOverride revoke(UUID player, String playerName, String actor, String reason) {
        return override(player, playerName, OverrideState.REVOKED, actor, reason);
    }

    /**
     * Synchronised so the registry update and the queued write stay in one order: on Folia two
     * threads can change one player at once, and the store must end up as the registry did.
     */
    private synchronized GrantOverride override(
            UUID player, String playerName, OverrideState state, String actor, String reason) {
        if (successor != null) {
            return successor.override(player, playerName, state, actor, reason);
        }
        GrantOverride override =
                new GrantOverride(player, playerName, state, actor, reason, clock.instant());
        GrantEvent event =
                new GrantEvent(
                        player,
                        playerName,
                        state == OverrideState.GRANTED
                                ? GrantEvent.Kind.GRANTED
                                : GrantEvent.Kind.REVOKED,
                        actor,
                        reason,
                        GrantEvent.NO_PLAYTIME,
                        override.at());
        registry.put(override);
        writer.accept(() -> store.putOverride(override, event));
        return override;
    }

    /**
     * Removes a by-hand decision so playtime decides again.
     *
     * @return whether there was an override to remove
     */
    public synchronized boolean reset(UUID player, String playerName, String actor) {
        if (successor != null) {
            return successor.reset(player, playerName, actor);
        }
        boolean removed = registry.remove(player);
        if (!removed) {
            return false;
        }
        GrantEvent event =
                new GrantEvent(
                        player,
                        playerName,
                        GrantEvent.Kind.RESET,
                        actor,
                        "",
                        GrantEvent.NO_PLAYTIME,
                        clock.instant());
        writer.accept(() -> store.removeOverride(player, event));
        return true;
    }

    /**
     * Records, once per player, that they were seen at or past the playtime threshold. Cheap to
     * call on every allowed action: after the first time it is a set lookup and nothing else.
     */
    public void recordEarned(UUID player, String playerName, long playtimeTicks) {
        // The common case, a player already on record, takes no lock.
        if (registry.hasEarned(player) && successor == null) {
            return;
        }
        recordEarnedOnce(player, playerName, playtimeTicks);
    }

    private synchronized void recordEarnedOnce(UUID player, String playerName, long playtimeTicks) {
        if (successor != null) {
            successor.recordEarned(player, playerName, playtimeTicks);
            return;
        }
        if (!registry.markEarned(player)) {
            return;
        }
        GrantEvent event =
                new GrantEvent(
                        player,
                        playerName,
                        GrantEvent.Kind.EARNED,
                        PLAYTIME_ACTOR,
                        "",
                        playtimeTicks,
                        clock.instant());
        writer.accept(() -> store.append(event));
    }

    /**
     * @param player the player to read
     * @param limit the maximum number of events
     * @return the player's most recent events, newest first, read from the store now
     */
    public List<GrantEvent> history(UUID player, int limit) {
        GrantService next = successor;
        return next == null ? store.history(player, limit) : next.history(player, limit);
    }
}
