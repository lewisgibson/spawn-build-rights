package com.lewisgibson.spawnbuildrights.core.time;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats human-friendly playtime durations expressed in Minecraft game ticks (20 ticks
 * per second).
 *
 * <p>Accepts compound values such as {@code "36h"}, {@code "1d12h"}, {@code "1d 12h"}, {@code
 * "90m"}, and {@code "45s"}. Recognised units are {@code d} (days), {@code h} (hours), {@code m}
 * (minutes), and {@code s} (seconds); matching is case-insensitive and internal whitespace is
 * ignored. Parsing is strict: any stray or unknown character causes an {@link
 * IllegalArgumentException} rather than a silently truncated value, so operator typos surface
 * immediately.
 */
public final class PlaytimeDuration {

    /** Ticks in one second of real time (the Minecraft server target tick rate). */
    public static final long TICKS_PER_SECOND = 20L;

    /** Ticks in one minute. */
    public static final long TICKS_PER_MINUTE = 60L * TICKS_PER_SECOND;

    /** Ticks in one hour. */
    public static final long TICKS_PER_HOUR = 60L * TICKS_PER_MINUTE;

    /** Ticks in one day. */
    public static final long TICKS_PER_DAY = 24L * TICKS_PER_HOUR;

    private static final Pattern WHOLE =
            Pattern.compile("(?:\\d+[dhms])+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN =
            Pattern.compile("(\\d+)([dhms])", Pattern.CASE_INSENSITIVE);

    /** The shortest duration written in days; anything shorter is written in hours. */
    private static final long DAYS_FROM = 7L * TICKS_PER_DAY;

    /** The unit letters, largest first, matching the order of {@link #parts}. */
    private static final char[] UNITS = {'d', 'h', 'm', 's'};

    /** The same units as words, singular. */
    private static final String[] UNIT_WORDS = {"day", "hour", "minute", "second"};

    private PlaytimeDuration() {}

    /**
     * Parses a compound duration string into ticks.
     *
     * @param value e.g. {@code "36h"}, {@code "1d12h"}, {@code "90m"}
     * @return the duration in ticks
     * @throws IllegalArgumentException if {@code value} is {@code null}, blank, contains no unit
     *     tokens, or contains any unexpected character
     * @throws ArithmeticException if the total overflows a {@code long}
     */
    public static long parseTicks(String value) {
        if (value == null) {
            throw new IllegalArgumentException("duration must not be null");
        }
        String compact = value.replaceAll("\\s+", "");
        if (compact.isEmpty()) {
            throw new IllegalArgumentException("duration must not be blank");
        }
        if (!WHOLE.matcher(compact).matches()) {
            throw new IllegalArgumentException(
                    "invalid duration '" + value + "' (expected e.g. '36h', '1d12h', '90m')");
        }
        Matcher matcher = TOKEN.matcher(compact);
        long ticks = 0L;
        while (matcher.find()) {
            long amount = Long.parseLong(matcher.group(1));
            long unitTicks =
                    switch (Character.toLowerCase(matcher.group(2).charAt(0))) {
                        case 'd' -> Math.multiplyExact(amount, TICKS_PER_DAY);
                        case 'h' -> Math.multiplyExact(amount, TICKS_PER_HOUR);
                        case 'm' -> Math.multiplyExact(amount, TICKS_PER_MINUTE);
                        // Only 's' can reach here, because the regex above allows just d/h/m/s.
                        // Folding seconds into the default keeps the switch exhaustive without an
                        // unreachable branch.
                        default -> Math.multiplyExact(amount, TICKS_PER_SECOND);
                    };
            ticks = Math.addExact(ticks, unitTicks);
        }
        return ticks;
    }

    /**
     * Formats a tick count into the compact form written back to config.yml, exact to the second
     * and without spaces (e.g. {@code 36h}, {@code 1h30m}, {@code 8d3h}). Hours are the largest
     * unit below a week, because that is how a requirement is thought about and written ({@code
     * 36h}, not {@code 1d12h}); days take over from seven days. Zero formats as {@code "0s"}. The
     * result always parses back to the same number of whole seconds.
     *
     * @param ticks the duration in ticks; must be {@code >= 0}
     * @return the compact string form
     * @throws IllegalArgumentException if {@code ticks} is negative
     */
    public static String format(long ticks) {
        long[] parts = parts(ticks);
        StringBuilder out = new StringBuilder();
        for (int unit = 0; unit < parts.length; unit++) {
            if (parts[unit] > 0) {
                out.append(parts[unit]).append(UNITS[unit]);
            }
        }
        return out.isEmpty() ? "0s" : out.toString();
    }

    /**
     * Formats a tick count for a player to read: the largest unit and, when it is not zero, the one
     * below it, separated by a space (e.g. {@code 36h}, {@code 33h 41m}, {@code 45m 10s}, {@code 8d
     * 3h}). Smaller units are dropped, because the seconds left on a 36 hour wait are noise. Hours
     * are the largest unit below a week, as in {@link #format}. The result also parses, to the
     * value with the dropped units removed.
     *
     * @param ticks the duration in ticks; must be {@code >= 0}
     * @return the short display form
     * @throws IllegalArgumentException if {@code ticks} is negative
     */
    public static String display(long ticks) {
        long[] parts = parts(ticks);
        for (int unit = 0; unit < parts.length; unit++) {
            if (parts[unit] > 0) {
                String largest = parts[unit] + String.valueOf(UNITS[unit]);
                boolean next = unit + 1 < parts.length && parts[unit + 1] > 0;
                return next ? largest + " " + parts[unit + 1] + UNITS[unit + 1] : largest;
            }
        }
        return "0s";
    }

    /**
     * Formats a tick count in words for a sentence a player reads: the largest unit and, when it is
     * not zero, the one below it, joined by "and" (e.g. {@code 36 hours}, {@code 14 hours and 31
     * minutes}, {@code 1 minute}, {@code 8 days and 3 hours}). The units are those of {@link
     * #display}, so the two always agree.
     *
     * @param ticks the duration in ticks; must be {@code >= 0}
     * @return the duration in words
     * @throws IllegalArgumentException if {@code ticks} is negative
     */
    public static String words(long ticks) {
        long[] parts = parts(ticks);
        for (int unit = 0; unit < parts.length; unit++) {
            if (parts[unit] > 0) {
                String largest = word(parts[unit], unit);
                boolean next = unit + 1 < parts.length && parts[unit + 1] > 0;
                return next ? largest + " and " + word(parts[unit + 1], unit + 1) : largest;
            }
        }
        return "0 seconds";
    }

    private static String word(long amount, int unit) {
        return amount + " " + UNIT_WORDS[unit] + (amount == 1 ? "" : "s");
    }

    /** Splits ticks into days, hours, minutes and seconds, with days only from a week up. */
    private static long[] parts(long ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be >= 0, was " + ticks);
        }
        long days = ticks >= DAYS_FROM ? ticks / TICKS_PER_DAY : 0;
        long remaining = ticks - days * TICKS_PER_DAY;
        long hours = remaining / TICKS_PER_HOUR;
        remaining %= TICKS_PER_HOUR;
        long minutes = remaining / TICKS_PER_MINUTE;
        remaining %= TICKS_PER_MINUTE;
        return new long[] {days, hours, minutes, remaining / TICKS_PER_SECOND};
    }
}
