package com.lewisgibson.spawnbuildrights.core.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GrantService")
class GrantServiceTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    /** Records every call, so the test can see what reached the store and in what order. */
    private static final class RecordingStore implements GrantStore {
        final Map<UUID, GrantOverride> overrides = new HashMap<>();
        final List<GrantEvent> events = new ArrayList<>();
        final List<String> calls = new ArrayList<>();

        @Override
        public void open() {}

        @Override
        public Map<UUID, GrantOverride> loadOverrides() {
            return overrides;
        }

        @Override
        public Set<UUID> loadEarned() {
            return new HashSet<>();
        }

        @Override
        public void putOverride(GrantOverride override, GrantEvent event) {
            calls.add("put");
            overrides.put(override.player(), override);
            events.add(event);
        }

        @Override
        public void removeOverride(UUID player, GrantEvent event) {
            calls.add("remove");
            overrides.remove(player);
            events.add(event);
        }

        @Override
        public void append(GrantEvent event) {
            calls.add("append");
            events.add(event);
        }

        @Override
        public List<GrantEvent> history(UUID player, int limit) {
            calls.add("history");
            return events;
        }

        @Override
        public void close() {}
    }

    private RecordingStore store;
    private GrantRegistry registry;
    private List<Runnable> queued;
    private GrantService service;

    @BeforeEach
    void setUp() {
        store = new RecordingStore();
        registry = new GrantRegistry();
        queued = new ArrayList<>();
        service = new GrantService(registry, store, queued::add, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void runQueued() {
        queued.forEach(Runnable::run);
        queued.clear();
    }

    @Test
    @DisplayName("rejects null collaborators")
    void rejectsNulls() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        assertThatNullPointerException()
                .isThrownBy(() -> new GrantService(null, store, queued::add, clock));
        assertThatNullPointerException()
                .isThrownBy(() -> new GrantService(registry, null, queued::add, clock));
        assertThatNullPointerException()
                .isThrownBy(() -> new GrantService(registry, store, null, clock));
        assertThatNullPointerException()
                .isThrownBy(() -> new GrantService(registry, store, queued::add, null));
        assertThat(service.registry()).isSameAs(registry);
    }

    @Test
    @DisplayName("grant updates the registry at once and writes the override and event later")
    void grant() {
        GrantOverride override = service.grant(STEVE, "Steve", "Alex", "trusted");

        assertThat(override)
                .isEqualTo(
                        new GrantOverride(
                                STEVE, "Steve", OverrideState.GRANTED, "Alex", "trusted", NOW));
        assertThat(registry.state(STEVE)).isEqualTo(OverrideState.GRANTED);
        assertThat(store.calls).isEmpty();

        runQueued();
        assertThat(store.calls).containsExactly("put");
        assertThat(store.overrides.get(STEVE)).isEqualTo(override);
        assertThat(store.events)
                .singleElement()
                .isEqualTo(
                        new GrantEvent(
                                STEVE,
                                "Steve",
                                GrantEvent.Kind.GRANTED,
                                "Alex",
                                "trusted",
                                -1,
                                NOW));
    }

    @Test
    @DisplayName("revoke does the same with the revoked state")
    void revoke() {
        service.revoke(STEVE, "Steve", "console", "grief");
        assertThat(registry.state(STEVE)).isEqualTo(OverrideState.REVOKED);
        runQueued();
        assertThat(store.overrides.get(STEVE).state()).isEqualTo(OverrideState.REVOKED);
        assertThat(store.events)
                .singleElement()
                .extracting(GrantEvent::kind)
                .isEqualTo(GrantEvent.Kind.REVOKED);
    }

    @Test
    @DisplayName("reset removes the override, logs it, and reports when there was nothing")
    void reset() {
        assertThat(service.reset(STEVE, "Steve", "console")).isFalse();
        assertThat(queued).isEmpty();

        service.grant(STEVE, "Steve", "Alex", "");
        runQueued();
        assertThat(service.reset(STEVE, "Steve", "console")).isTrue();
        assertThat(registry.state(STEVE)).isEqualTo(OverrideState.NONE);
        runQueued();
        assertThat(store.overrides).isEmpty();
        assertThat(store.events)
                .extracting(GrantEvent::kind)
                .containsExactly(GrantEvent.Kind.GRANTED, GrantEvent.Kind.RESET);
    }

    @Test
    @DisplayName("recordEarned writes one event per player, ever")
    void recordEarned() {
        service.recordEarned(STEVE, "Steve", 2_592_000L);
        service.recordEarned(STEVE, "Steve", 2_600_000L);
        runQueued();
        assertThat(store.events)
                .singleElement()
                .isEqualTo(
                        new GrantEvent(
                                STEVE,
                                "Steve",
                                GrantEvent.Kind.EARNED,
                                GrantService.PLAYTIME_ACTOR,
                                "",
                                2_592_000L,
                                NOW));
        assertThat(registry.hasEarned(STEVE)).isTrue();
    }

    @Test
    @DisplayName("history reads the store on the calling thread")
    void history() {
        service.grant(STEVE, "Steve", "Alex", "");
        runQueued();
        assertThat(service.history(STEVE, 5)).hasSize(1);
        assertThat(store.calls).endsWith("history");
    }

    @Test
    @DisplayName("after a hand-over, every decision through the old service lands in the new one")
    void handOverPassesEverythingOn() {
        RecordingStore newStore = new RecordingStore();
        GrantRegistry newRegistry = new GrantRegistry();
        List<Runnable> newQueue = new ArrayList<>();
        GrantService next =
                new GrantService(
                        newRegistry, newStore, newQueue::add, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.handOver(() -> next)).isSameAs(next);
        service.grant(STEVE, "Steve", "Alex", "");
        UUID alex = UUID.fromString("00000000-0000-0000-0000-000000000002");
        service.revoke(alex, "Alex", "console", "");
        assertThat(service.reset(alex, "Alex", "console")).isTrue();
        service.recordEarned(STEVE, "Steve", 2_592_000L);
        newQueue.forEach(Runnable::run);

        assertThat(registry.state(STEVE)).isEqualTo(OverrideState.NONE);
        assertThat(registry.hasEarned(STEVE)).isFalse();
        assertThat(queued).isEmpty();
        assertThat(newRegistry.state(STEVE)).isEqualTo(OverrideState.GRANTED);
        assertThat(newRegistry.hasEarned(STEVE)).isTrue();
        assertThat(newStore.events)
                .extracting(GrantEvent::kind)
                .containsExactly(
                        GrantEvent.Kind.GRANTED,
                        GrantEvent.Kind.REVOKED,
                        GrantEvent.Kind.RESET,
                        GrantEvent.Kind.EARNED);
        assertThat(service.history(STEVE, 5)).isSameAs(newStore.events);
        assertThat(store.calls).isEmpty();
    }

    @Test
    @DisplayName("an earned player already on record before the hand-over is passed on too")
    void earnedAfterHandOver() {
        service.recordEarned(STEVE, "Steve", 2_592_000L);
        RecordingStore newStore = new RecordingStore();
        GrantService next =
                new GrantService(new GrantRegistry(), newStore, Runnable::run, Clock.systemUTC());
        service.handOver(() -> next);

        service.recordEarned(STEVE, "Steve", 2_600_000L);

        assertThat(newStore.events)
                .singleElement()
                .extracting(GrantEvent::playtimeTicks)
                .isEqualTo(2_600_000L);
    }

    @Test
    @DisplayName("a second hand-over of a service already handed over moves its successor on")
    void handOverTwice() {
        GrantService second =
                new GrantService(new GrantRegistry(), store, queued::add, Clock.systemUTC());
        GrantService third =
                new GrantService(new GrantRegistry(), store, queued::add, Clock.systemUTC());
        service.handOver(() -> second);

        assertThat(service.handOver(() -> third)).isSameAs(third);
        service.grant(STEVE, "Steve", "Alex", "");
        assertThat(third.registry().state(STEVE)).isEqualTo(OverrideState.GRANTED);
        assertThat(second.registry().state(STEVE)).isEqualTo(OverrideState.NONE);
    }

    @Test
    @DisplayName("a hand-over whose new service cannot be built hands nothing over")
    void failedHandOver() {
        assertThatThrownBy(
                        () ->
                                service.handOver(
                                        () -> {
                                            throw new StorageException("unreadable", null);
                                        }))
                .isInstanceOf(StorageException.class);
        assertThatNullPointerException().isThrownBy(() -> service.handOver(() -> null));

        service.grant(STEVE, "Steve", "Alex", "");
        assertThat(registry.state(STEVE)).isEqualTo(OverrideState.GRANTED);
    }

    @Test
    @DisplayName(
            "two threads seeing a player as not yet earned write one earned event between them")
    void earnedRaceWritesOnce() throws Exception {
        Thread racer;
        synchronized (service) {
            racer = new Thread(() -> service.recordEarned(STEVE, "Steve", 2_592_000L));
            racer.start();
            // The racer has passed the lock-free check and waits for the lock.
            while (racer.getState() != Thread.State.BLOCKED) {
                Thread.onSpinWait();
            }
            service.recordEarned(STEVE, "Steve", 2_592_000L);
        }
        racer.join(5_000);
        runQueued();

        assertThat(store.events).hasSize(1);
    }
}
