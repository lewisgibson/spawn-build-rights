package com.lewisgibson.spawnbuildrights.core.access;

/**
 * What the plugin's own store says about a player, consulted alongside the permission node.
 *
 * <p>An override is a decision made by hand through the plugin's commands ({@code /sbr grant},
 * {@code /sbr revoke}) and kept in the plugin's storage, so it works on a server with no
 * permissions plugin at all. Most players have {@link #NONE}.
 */
public enum OverrideState {

    /** Granted by hand: the player can build now, whatever their playtime. */
    GRANTED,

    /** Revoked by hand: the player cannot build in the region, however long they play. */
    REVOKED,

    /** No override recorded, so the permission node and then playtime decide. */
    NONE
}
