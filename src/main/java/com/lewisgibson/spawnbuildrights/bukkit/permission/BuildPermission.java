package com.lewisgibson.spawnbuildrights.bukkit.permission;

import com.lewisgibson.spawnbuildrights.core.access.PermissionState;
import org.bukkit.permissions.Permissible;

/**
 * Reads the three-way state of the build permission node from Bukkit's {@link Permissible}.
 *
 * <p>Bukkit exposes a node as two booleans: {@code hasPermission} (does it resolve true) and {@code
 * isPermissionSet} (is there a value at all). Together they give the tristate the policy needs. The
 * order matters: {@code hasPermission} is asked first because an operator's default grant is
 * reported as a value by plain Bukkit but not by LuckPerms, whose {@code isPermissionSet} answers
 * false for anything that resolved through the op or default-permission processors. Asking {@code
 * hasPermission} first makes both answer {@link PermissionState#GRANTED} for an operator.
 */
public final class BuildPermission {

    private BuildPermission() {}

    /**
     * @param permissible the player (or other permissible) to read
     * @param node the build permission node name
     * @return {@link PermissionState#GRANTED} when the node resolves true, {@link
     *     PermissionState#REVOKED} when it is explicitly set false, {@link PermissionState#UNSET}
     *     when it is not set either way
     */
    public static PermissionState of(Permissible permissible, String node) {
        if (permissible.hasPermission(node)) {
            return PermissionState.GRANTED;
        }
        if (permissible.isPermissionSet(node)) {
            return PermissionState.REVOKED;
        }
        return PermissionState.UNSET;
    }
}
