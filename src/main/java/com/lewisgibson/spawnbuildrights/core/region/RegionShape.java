package com.lewisgibson.spawnbuildrights.core.region;

import java.util.Locale;

/**
 * The geometric shape of a {@link SpawnRegion}'s horizontal footprint.
 *
 * <p>The default, {@link #SQUARE}, mirrors vanilla Minecraft's {@code spawn-protection} area, which
 * is a square centred on the world spawn. {@link #CIRCLE} is offered for servers that prefer a
 * rounded boundary.
 */
public enum RegionShape {

    /**
     * A square footprint: every column within {@code radius} blocks on both axes (Chebyshev
     * distance).
     */
    SQUARE,

    /**
     * A circular footprint: every column within {@code radius} blocks of the centre (Euclidean
     * distance).
     */
    CIRCLE;

    /**
     * Parses a shape from a case-insensitive configuration token.
     *
     * @param value the configured value, e.g. {@code "square"} or {@code "circle"}
     * @return the matching shape
     * @throws IllegalArgumentException if {@code value} is {@code null} or not a recognised shape
     */
    public static RegionShape fromToken(String value) {
        if (value == null) {
            throw new IllegalArgumentException("region shape must not be null");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "square" -> SQUARE;
            case "circle", "round" -> CIRCLE;
            default ->
                    throw new IllegalArgumentException(
                            "unknown region shape '" + value + "' (expected 'square' or 'circle')");
        };
    }

    /**
     * @return the lower-case token used to represent this shape in configuration files
     */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }
}
