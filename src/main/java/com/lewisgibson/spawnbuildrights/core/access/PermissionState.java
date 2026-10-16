package com.lewisgibson.spawnbuildrights.core.access;

/**
 * What the server's permission system says about a player's build node, consulted before playtime.
 *
 * <p>The node is the one place a decision made by hand lives, so the permissions plugin (LuckPerms
 * or whatever the server runs) stays the single surface for granting early and for revoking. The
 * common state for an ordinary player is {@link #UNSET}, where accumulated playtime decides.
 */
public enum PermissionState {

    /** The node resolves true: an operator, or a player granted build access by hand. */
    GRANTED,

    /**
     * The node is explicitly set false: build access was revoked by hand, and no amount of playtime
     * restores it until the node is unset again.
     */
    REVOKED,

    /** The node is not set either way, so accumulated playtime decides. */
    UNSET
}
