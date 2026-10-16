package com.lewisgibson.spawnbuildrights.core.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("BuildAccessPolicy")
class BuildAccessPolicyTest {

    private static final long THIRTY_SIX_HOURS = 2_592_000L;

    private final BuildAccessPolicy policy = new BuildAccessPolicy(THIRTY_SIX_HOURS);

    @Test
    @DisplayName("rejects a negative requirement")
    void rejectsNegativeRequirement() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BuildAccessPolicy(-1));
    }

    @Test
    @DisplayName("rejects a null permission state")
    void rejectsNullPermission() {
        assertThatNullPointerException()
                .isThrownBy(() -> policy.evaluate(0, null, OverrideState.NONE));
    }

    @Test
    @DisplayName("rejects a null override state")
    void rejectsNullOverride() {
        assertThatNullPointerException()
                .isThrownBy(() -> policy.evaluate(0, PermissionState.UNSET, null));
    }

    @ParameterizedTest(name = "permission {0}, override {1}, playtime {2}h -> {3} ({4})")
    @CsvSource({
        // a revocation anywhere denies, whatever else is true
        "REVOKED, NONE,    1000, false, PERMISSION_REVOKED",
        "REVOKED, GRANTED, 1000, false, PERMISSION_REVOKED",
        "REVOKED, REVOKED, 1000, false, PERMISSION_REVOKED",
        "GRANTED, REVOKED, 1000, false, OVERRIDE_REVOKED",
        "UNSET,   REVOKED, 1000, false, OVERRIDE_REVOKED",
        // then a grant anywhere allows, whatever the clock says
        "GRANTED, NONE,    0,    true,  PERMISSION_GRANTED",
        "GRANTED, GRANTED, 0,    true,  PERMISSION_GRANTED",
        "UNSET,   GRANTED, 0,    true,  OVERRIDE_GRANTED",
        // only then does playtime decide
        "UNSET,   NONE,    36,   true,  THRESHOLD_MET",
        "UNSET,   NONE,    37,   true,  THRESHOLD_MET",
        "UNSET,   NONE,    35,   false, BELOW_THRESHOLD",
        "UNSET,   NONE,    0,    false, BELOW_THRESHOLD",
    })
    void decides(
            PermissionState permission,
            OverrideState override,
            long playtimeHours,
            boolean allowed,
            DecisionReason reason) {
        AccessResult result = policy.evaluate(playtimeHours * 72_000L, permission, override);
        assertThat(result.allowed()).isEqualTo(allowed);
        assertThat(result.reason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("a zero requirement allows anyone nothing was set for")
    void zeroRequirementAlwaysAllows() {
        AccessResult result =
                new BuildAccessPolicy(0).evaluate(0, PermissionState.UNSET, OverrideState.NONE);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isEqualTo(DecisionReason.THRESHOLD_MET);
    }

    @Test
    @DisplayName("the two revocation reasons, and only those, read as revoked")
    void revokedReasons() {
        assertThat(DecisionReason.PERMISSION_REVOKED.revoked()).isTrue();
        assertThat(DecisionReason.OVERRIDE_REVOKED.revoked()).isTrue();
        assertThat(DecisionReason.PERMISSION_GRANTED.revoked()).isFalse();
        assertThat(DecisionReason.OVERRIDE_GRANTED.revoked()).isFalse();
        assertThat(DecisionReason.THRESHOLD_MET.revoked()).isFalse();
        assertThat(DecisionReason.BELOW_THRESHOLD.revoked()).isFalse();
    }
}
