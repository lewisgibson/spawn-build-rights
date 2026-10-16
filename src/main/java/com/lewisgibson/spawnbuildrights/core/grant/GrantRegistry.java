package com.lewisgibson.spawnbuildrights.core.grant;

import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The in-memory copy of the store, read by the per-event decision so the hot path never touches a
 * database. Loaded once from a {@link GrantStore} and kept in step by the code that writes to it.
 *
 * <p>Thread-safe: reads happen on the server's event threads while writes arrive from commands and
 * from event threads ({@link #markEarned}).
 */
public final class GrantRegistry {

    private final Map<UUID, GrantOverride> overrides = new ConcurrentHashMap<>();
    private final Set<UUID> earned = ConcurrentHashMap.newKeySet();

    /** Creates an empty registry. */
    public GrantRegistry() {}

    /**
     * Loads a registry from an already-open store.
     *
     * @param store the store to read
     * @return a registry holding every override and every earned player the store has
     */
    public static GrantRegistry load(GrantStore store) {
        Objects.requireNonNull(store, "store");
        GrantRegistry registry = new GrantRegistry();
        registry.overrides.putAll(store.loadOverrides());
        registry.earned.addAll(store.loadEarned());
        return registry;
    }

    /**
     * @param player the player to look up
     * @return the player's override state, {@link OverrideState#NONE} when none is recorded
     */
    public OverrideState state(UUID player) {
        GrantOverride override = overrides.get(player);
        return override == null ? OverrideState.NONE : override.state();
    }

    /**
     * @param player the player to look up
     * @return the player's override, if one is recorded
     */
    public Optional<GrantOverride> override(UUID player) {
        return Optional.ofNullable(overrides.get(player));
    }

    /** Records or replaces an override. */
    public void put(GrantOverride override) {
        Objects.requireNonNull(override, "override");
        overrides.put(override.player(), override);
    }

    /**
     * Removes a player's override.
     *
     * @param player the player
     * @return whether there was one to remove
     */
    public boolean remove(UUID player) {
        return overrides.remove(player) != null;
    }

    /**
     * Marks a player as having earned build access through playtime.
     *
     * @param player the player
     * @return {@code true} the first time, so the caller writes exactly one earned event
     */
    public boolean markEarned(UUID player) {
        return earned.add(player);
    }

    /**
     * @param player the player
     * @return whether an earned event is already on record for the player
     */
    public boolean hasEarned(UUID player) {
        return earned.contains(player);
    }

    /**
     * @return the number of overrides held
     */
    public int overrideCount() {
        return overrides.size();
    }
}
