package com.lewisgibson.spawnbuildrights.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.lewisgibson.spawnbuildrights.core.settings.PortalArrivals.Kind;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("PortalArrivals")
class PortalArrivalsTest {

    @Test
    void defaultsDefuseEveryKind() {
        for (Kind kind : Kind.values()) {
            assertThat(PortalArrivals.defaults().defuses(kind)).as(kind.name()).isTrue();
        }
        assertThat(PortalArrivals.defaults()).isEqualTo(PortalArrivals.all(true));
    }

    @Test
    void allOffDefusesNothing() {
        for (Kind kind : Kind.values()) {
            assertThat(PortalArrivals.all(false).defuses(kind)).as(kind.name()).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void eachKindHasItsOwnSwitch(Kind only) {
        PortalArrivals arrivals =
                new PortalArrivals(
                        only == Kind.EXPLOSIVE,
                        only == Kind.WITHER_SKULL,
                        only == Kind.FIREBALL,
                        only == Kind.PROJECTILE,
                        only == Kind.FIREWORK,
                        only == Kind.FALLING_BLOCK);
        for (Kind kind : Kind.values()) {
            assertThat(arrivals.defuses(kind)).as(kind.name()).isEqualTo(kind == only);
        }
        assertThat(EnumSet.allOf(Kind.class).stream().filter(arrivals::defuses)).containsOnly(only);
    }

    @Test
    void rejectsANullKind() {
        assertThatNullPointerException().isThrownBy(() -> PortalArrivals.defaults().defuses(null));
    }

    @Test
    void environmentalProtectionRequiresPortalArrivals() {
        assertThat(EnvironmentalProtection.defaults().portalArrivals())
                .isEqualTo(PortalArrivals.defaults());
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new EnvironmentalProtection(
                                        true, true, true, true, true, true, true, true, true,
                                        null));
    }
}
