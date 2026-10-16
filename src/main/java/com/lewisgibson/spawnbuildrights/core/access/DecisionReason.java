package com.lewisgibson.spawnbuildrights.core.access;

/**
 * Why a build action inside the spawn region was allowed or denied, for messaging and diagnostics.
 */
public enum DecisionReason {

    /** The player's build node is set false. A revocation anywhere beats every grant. */
    PERMISSION_REVOKED,

    /** The plugin's own store holds a revocation for the player. */
    OVERRIDE_REVOKED,

    /**
     * The player's build node is set true (an operator, or a by-hand grant in the permission
     * plugin).
     */
    PERMISSION_GRANTED,

    /** The plugin's own store holds a grant for the player. */
    OVERRIDE_GRANTED,

    /** Nothing was set by hand and the player has met or exceeded the required playtime. */
    THRESHOLD_MET,

    /** Nothing was set by hand and the player has not yet played long enough. */
    BELOW_THRESHOLD;

    /**
     * @return whether this reason is one of the two revocations, which pick the revoked message
     */
    public boolean revoked() {
        return this == PERMISSION_REVOKED || this == OVERRIDE_REVOKED;
    }
}
