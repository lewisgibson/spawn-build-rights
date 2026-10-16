package com.lewisgibson.spawnbuildrights.core.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GrantRegistry")
class GrantRegistryTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant AT = Instant.parse("2026-09-10T12:00:00Z");

    private static GrantOverride granted(UUID player) {
        return new GrantOverride(player, "name", OverrideState.GRANTED, "console", "", AT);
    }

    /** A store that answers from maps, enough to prove load() reads both halves. */
    private static final class FakeGrantStore implements GrantStore {
        final Map<UUID, GrantOverride> overrides = new HashMap<>();
        final Set<UUID> earned = new HashSet<>();

        @Override
        public void open() {}

        @Override
        public Map<UUID, GrantOverride> loadOverrides() {
            return overrides;
        }

        @Override
        public Set<UUID> loadEarned() {
            return earned;
        }

        @Override
        public void putOverride(GrantOverride override, GrantEvent event) {
            overrides.put(override.player(), override);
        }

        @Override
        public void removeOverride(UUID player, GrantEvent event) {
            overrides.remove(player);
        }

        @Override
        public void append(GrantEvent event) {}

        @Override
        public List<GrantEvent> history(UUID player, int limit) {
            return new ArrayList<>();
        }

        @Override
        public void close() {}
    }

    @Test
    @DisplayName("starts empty")
    void startsEmpty() {
        GrantRegistry registry = new GrantRegistry();
        assertThat(registry.state(STEVE)).isEqualTo(OverrideState.NONE);
        assertThat(registry.override(STEVE)).isEmpty();
        assertThat(registry.hasEarned(STEVE)).isFalse();
        assertThat(registry.overrideCount()).isZero();
    }

    @Test
    @DisplayName("loads every override and every earned player from the store")
    void loadsFromStore() {
        FakeGrantStore store = new FakeGrantStore();
        store.overrides.put(STEVE, granted(STEVE));
        store.earned.add(ALEX);

        GrantRegistry registry = GrantRegistry.load(store);

        assertThat(registry.state(STEVE)).isEqualTo(OverrideState.GRANTED);
        assertThat(registry.override(STEVE)).contains(granted(STEVE));
        assertThat(registry.hasEarned(ALEX)).isTrue();
        assertThat(registry.hasEarned(STEVE)).isFalse();
        assertThat(registry.overrideCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("refuses a null store")
    void refusesNullStore() {
        assertThatNullPointerException().isThrownBy(() -> GrantRegistry.load(null));
    }

    @Test
    @DisplayName("put replaces and remove reports whether there was one")
    void putAndRemove() {
        GrantRegistry registry = new GrantRegistry();
        registry.put(granted(STEVE));
        assertThat(registry.state(STEVE)).isEqualTo(OverrideState.GRANTED);

        registry.put(new GrantOverride(STEVE, "Steve", OverrideState.REVOKED, "Alex", "grief", AT));
        assertThat(registry.state(STEVE)).isEqualTo(OverrideState.REVOKED);
        assertThat(registry.overrideCount()).isEqualTo(1);

        assertThat(registry.remove(STEVE)).isTrue();
        assertThat(registry.remove(STEVE)).isFalse();
        assertThat(registry.state(STEVE)).isEqualTo(OverrideState.NONE);
    }

    @Test
    @DisplayName("refuses a null override")
    void refusesNullOverride() {
        assertThatNullPointerException().isThrownBy(() -> new GrantRegistry().put(null));
    }

    @Test
    @DisplayName("markEarned is true exactly once per player")
    void markEarnedOnce() {
        GrantRegistry registry = new GrantRegistry();
        assertThat(registry.markEarned(STEVE)).isTrue();
        assertThat(registry.markEarned(STEVE)).isFalse();
        assertThat(registry.hasEarned(STEVE)).isTrue();
        assertThat(registry.markEarned(ALEX)).isTrue();
    }
}
