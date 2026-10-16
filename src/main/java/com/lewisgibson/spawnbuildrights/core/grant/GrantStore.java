package com.lewisgibson.spawnbuildrights.core.grant;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Durable storage for the state the plugin actually has to keep: the by-hand overrides and the
 * audit log. Playtime is never stored - the game already keeps it.
 *
 * <p>Implementations are opened once, read in full at startup into a {@link GrantRegistry}, written
 * through as decisions are made, and closed on shutdown. Every method throws {@link
 * StorageException} on failure. Nothing here is on the hot path: the per-event decision reads the
 * in-memory registry only.
 */
public interface GrantStore {

    /** Opens the store and creates or upgrades its schema. */
    void open();

    /**
     * @return every stored override, keyed by player
     */
    Map<UUID, GrantOverride> loadOverrides();

    /**
     * @return every player with an {@link GrantEvent.Kind#EARNED} event on record
     */
    Set<UUID> loadEarned();

    /**
     * Inserts or replaces the override for {@code override.player()} and appends its audit event,
     * together: both are stored or neither is.
     */
    void putOverride(GrantOverride override, GrantEvent event);

    /**
     * Removes the override for {@code player}, if any, and appends its audit event, together: both
     * are stored or neither is.
     */
    void removeOverride(UUID player, GrantEvent event);

    /** Appends one event to the audit log. */
    void append(GrantEvent event);

    /**
     * @param player the player to read
     * @param limit the maximum number of events to return
     * @return the player's most recent events, newest first
     */
    List<GrantEvent> history(UUID player, int limit);

    /** Closes the store. Idempotent. */
    void close();
}
