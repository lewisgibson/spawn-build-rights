package com.lewisgibson.spawnbuildrights.core.message;

import com.lewisgibson.spawnbuildrights.core.access.AccessResult;
import java.util.Objects;

/**
 * The player-facing message templates, as raw strings.
 *
 * <p>Templates may contain MiniMessage markup, which the platform adapter resolves when sending;
 * the pure core only substitutes {@code %name%} placeholders (see {@link Placeholders}). This keeps
 * message text operator-editable in configuration without coupling the domain to any text library.
 * The first three are the one sentence a player sees about their own build rights: on the action
 * bar when a change is refused, and in chat when they run {@code /sbr}.
 *
 * @param buildAllowed told to a player who can build at spawn; supports {@code %required%}, {@code
 *     %played%}, {@code %remaining%}
 * @param buildDenied told to a player below the playtime requirement; supports the same
 *     placeholders
 * @param buildRevoked told to a player whose build rights were removed by hand; supports the same
 *     placeholders
 * @param operatorConflictWarning shown to operators on join when vanilla spawn-protection is
 *     enabled; supports {@code %radius%}
 */
public record Messages(
        String buildAllowed,
        String buildDenied,
        String buildRevoked,
        String operatorConflictWarning) {

    public Messages {
        Objects.requireNonNull(buildAllowed, "buildAllowed");
        Objects.requireNonNull(buildDenied, "buildDenied");
        Objects.requireNonNull(buildRevoked, "buildRevoked");
        Objects.requireNonNull(operatorConflictWarning, "operatorConflictWarning");
    }

    /**
     * @param result a player's build decision
     * @return the sentence that tells them about it: allowed, revoked by hand, or still earning
     */
    public String about(AccessResult result) {
        if (result.allowed()) {
            return buildAllowed;
        }
        return result.reason().revoked() ? buildRevoked : buildDenied;
    }
}
