package com.lewisgibson.spawnbuildrights.core.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AccessResult")
class AccessResultTest {

    @Test
    @DisplayName("allowed() builds a permitted result carrying the reason")
    void allowedFactory() {
        AccessResult result = AccessResult.allowed(DecisionReason.PERMISSION_GRANTED);
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isEqualTo(DecisionReason.PERMISSION_GRANTED);
    }

    @Test
    @DisplayName("denied() builds a rejected result carrying the reason")
    void deniedFactory() {
        AccessResult result = AccessResult.denied(DecisionReason.BELOW_THRESHOLD);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo(DecisionReason.BELOW_THRESHOLD);
    }

    @Test
    @DisplayName("rejects a null reason")
    void rejectsNullReason() {
        assertThatNullPointerException().isThrownBy(() -> new AccessResult(true, null));
    }
}
