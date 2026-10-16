package com.lewisgibson.spawnbuildrights.core.message;

import com.lewisgibson.spawnbuildrights.core.time.PlaytimeDuration;
import java.util.Map;
import java.util.Objects;

/**
 * Substitutes {@code %name%} placeholders in a message template.
 *
 * <p>Pure and platform-free: it operates on plain strings so it can be unit-tested without a server
 * and reused by any platform adapter. Colour/markup (MiniMessage) is resolved later, by the
 * adapter, after placeholders have been applied.
 */
public final class Placeholders {

    private Placeholders() {}

    /**
     * Replaces every {@code %key%} occurrence in {@code template} with its mapped value.
     *
     * <p>Unknown placeholders are left verbatim so a typo in a template surfaces to the operator
     * rather than silently rendering as an empty string.
     *
     * @param template the template containing {@code %key%} tokens
     * @param values the placeholder values, keyed by name (without the surrounding {@code %})
     * @return the template with all known placeholders substituted
     */
    public static String apply(String template, Map<String, String> values) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(values, "values");
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace('%' + entry.getKey() + '%', entry.getValue());
        }
        return result;
    }

    /**
     * The values a build rights sentence can use, each in words: {@code %required%}, {@code
     * %played%} and {@code %remaining%}, which is never below zero.
     *
     * @param playedTicks the player's playtime
     * @param requiredTicks the configured requirement
     * @return the placeholder values keyed by name
     */
    public static Map<String, String> playtime(long playedTicks, long requiredTicks) {
        return Map.of(
                "required", PlaytimeDuration.words(requiredTicks),
                "played", PlaytimeDuration.words(playedTicks),
                "remaining", PlaytimeDuration.words(Math.max(0L, requiredTicks - playedTicks)));
    }
}
