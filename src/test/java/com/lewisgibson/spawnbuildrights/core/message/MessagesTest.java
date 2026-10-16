package com.lewisgibson.spawnbuildrights.core.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.lewisgibson.spawnbuildrights.core.access.AccessResult;
import com.lewisgibson.spawnbuildrights.core.access.DecisionReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Messages")
class MessagesTest {

    @Test
    @DisplayName("exposes every template")
    void exposesTemplates() {
        Messages messages = new Messages("allowed", "denied", "revoked", "warn");
        assertThat(messages.buildAllowed()).isEqualTo("allowed");
        assertThat(messages.buildDenied()).isEqualTo("denied");
        assertThat(messages.buildRevoked()).isEqualTo("revoked");
        assertThat(messages.operatorConflictWarning()).isEqualTo("warn");
    }

    @Test
    @DisplayName("rejects a null buildAllowed template")
    void rejectsNullBuildAllowed() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Messages(null, "denied", "revoked", "warn"));
    }

    @Test
    @DisplayName("rejects a null buildDenied template")
    void rejectsNullBuildDenied() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Messages("allowed", null, "revoked", "warn"));
    }

    @Test
    @DisplayName("rejects a null buildRevoked template")
    void rejectsNullBuildRevoked() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Messages("allowed", "denied", null, "warn"));
    }

    @Test
    @DisplayName("rejects a null operatorConflictWarning template")
    void rejectsNullOperatorWarning() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Messages("allowed", "denied", "revoked", null));
    }

    @Test
    @DisplayName("about picks the sentence for an allowed, revoked or still earning player")
    void aboutPicksTheSentence() {
        Messages messages = new Messages("allowed", "denied", "revoked", "warn");
        assertThat(messages.about(AccessResult.allowed(DecisionReason.THRESHOLD_MET)))
                .isEqualTo("allowed");
        assertThat(messages.about(AccessResult.allowed(DecisionReason.OVERRIDE_GRANTED)))
                .isEqualTo("allowed");
        assertThat(messages.about(AccessResult.denied(DecisionReason.BELOW_THRESHOLD)))
                .isEqualTo("denied");
        assertThat(messages.about(AccessResult.denied(DecisionReason.PERMISSION_REVOKED)))
                .isEqualTo("revoked");
        assertThat(messages.about(AccessResult.denied(DecisionReason.OVERRIDE_REVOKED)))
                .isEqualTo("revoked");
    }
}
