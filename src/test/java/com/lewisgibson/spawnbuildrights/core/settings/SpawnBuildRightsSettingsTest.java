package com.lewisgibson.spawnbuildrights.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.lewisgibson.spawnbuildrights.core.message.Messages;
import com.lewisgibson.spawnbuildrights.core.region.RegionShape;
import com.lewisgibson.spawnbuildrights.core.region.SpawnRegion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SpawnBuildRightsSettings")
class SpawnBuildRightsSettingsTest {

    private static final SpawnRegion REGION =
            new SpawnRegion("world", 0, 0, 16, RegionShape.SQUARE, 0, 320);
    private static final Messages MESSAGES = new Messages("allowed", "denied", "revoked", "warn");
    private static final String PERM = "spawnbuildrights.build";
    private static final StorageSettings STORAGE = StorageSettings.sqliteDefault();

    private static SpawnBuildRightsSettings valid() {
        return new SpawnBuildRightsSettings(
                REGION,
                2_592_000L,
                PERM,
                true,
                true,
                MESSAGES,
                STORAGE,
                EnvironmentalProtection.defaults());
    }

    @Test
    @DisplayName("exposes its fields when valid")
    void buildsValid() {
        SpawnBuildRightsSettings settings = valid();
        assertThat(settings.region()).isEqualTo(REGION);
        assertThat(settings.requiredPlaytimeTicks()).isEqualTo(2_592_000L);
        assertThat(settings.buildPermission()).isEqualTo(PERM);
        assertThat(settings.warnOnVanillaSpawnProtection()).isTrue();
        assertThat(settings.alertOperatorsOnJoin()).isTrue();
        assertThat(settings.messages()).isEqualTo(MESSAGES);
        assertThat(settings.storage()).isEqualTo(STORAGE);
    }

    @Test
    void defaultsProtectBuildsAndDisplays() {
        EnvironmentalProtection protection = valid().protection();
        assertThat(protection.explosions()).isTrue();
        assertThat(protection.hostileMobChanges()).isTrue();
        assertThat(protection.displayDamage()).isTrue();
        assertThat(protection.fireDamage()).isTrue();
        assertThat(protection.fallingBlocks()).isTrue();
        assertThat(protection.fluidFlow()).isTrue();
        assertThat(protection.pistons()).isTrue();
        assertThat(protection.dispensers()).isTrue();
        assertThat(protection.growth()).isTrue();
        assertThat(protection.portalArrivals()).isEqualTo(PortalArrivals.defaults());
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new SpawnBuildRightsSettings(
                                        REGION, 0, PERM, true, true, MESSAGES, STORAGE, null));
    }

    @Test
    @DisplayName("rejects null storage settings")
    void rejectsNullStorage() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new SpawnBuildRightsSettings(
                                        REGION,
                                        0,
                                        PERM,
                                        true,
                                        true,
                                        MESSAGES,
                                        null,
                                        EnvironmentalProtection.defaults()));
    }

    @Test
    @DisplayName("rejects a null region")
    void rejectsNullRegion() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new SpawnBuildRightsSettings(
                                        null,
                                        0,
                                        PERM,
                                        true,
                                        true,
                                        MESSAGES,
                                        STORAGE,
                                        EnvironmentalProtection.defaults()));
    }

    @Test
    @DisplayName("rejects a null build permission")
    void rejectsNullBuildPermission() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new SpawnBuildRightsSettings(
                                        REGION,
                                        0,
                                        null,
                                        true,
                                        true,
                                        MESSAGES,
                                        STORAGE,
                                        EnvironmentalProtection.defaults()));
    }

    @Test
    @DisplayName("rejects null messages")
    void rejectsNullMessages() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new SpawnBuildRightsSettings(
                                        REGION,
                                        0,
                                        PERM,
                                        true,
                                        true,
                                        null,
                                        STORAGE,
                                        EnvironmentalProtection.defaults()));
    }

    @Test
    @DisplayName("rejects a negative playtime requirement")
    void rejectsNegativePlaytime() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new SpawnBuildRightsSettings(
                                        REGION,
                                        -1,
                                        PERM,
                                        true,
                                        true,
                                        MESSAGES,
                                        STORAGE,
                                        EnvironmentalProtection.defaults()));
    }

    @Test
    @DisplayName("withRegion replaces the region and nothing else")
    void withRegionReplacesOnlyTheRegion() {
        SpawnBuildRightsSettings settings = valid();
        SpawnRegion moved = settings.region().centredAt(500, -500);

        SpawnBuildRightsSettings changed = settings.withRegion(moved);

        assertThat(changed.region()).isEqualTo(moved);
        assertThat(changed.withRegion(settings.region())).isEqualTo(settings);
    }
}
