package com.lewisgibson.spawnbuildrights.core.access;

import java.util.Objects;

/**
 * The outcome of a build-access check: whether the action is permitted, and the {@link
 * DecisionReason} that explains it (used to pick the right player-facing message).
 *
 * @param allowed whether the build action is permitted
 * @param reason the reason for the decision
 */
public record AccessResult(boolean allowed, DecisionReason reason) {

    /** Requires a reason so every decision can be explained to the player. */
    public AccessResult {
        Objects.requireNonNull(reason, "reason");
    }

    /**
     * @return an allowed result with the given reason
     */
    public static AccessResult allowed(DecisionReason reason) {
        return new AccessResult(true, reason);
    }

    /**
     * @return a denied result with the given reason
     */
    public static AccessResult denied(DecisionReason reason) {
        return new AccessResult(false, reason);
    }
}
