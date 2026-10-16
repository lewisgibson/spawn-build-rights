package com.lewisgibson.spawnbuildrights.core.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("SpawnRegion")
class SpawnRegionTest {

    private static final String WORLD = "world";

    private static SpawnRegion square(int radius) {
        return new SpawnRegion(WORLD, 0, 0, radius, RegionShape.SQUARE, 0, 320);
    }

    private static SpawnRegion circle(int radius) {
        return new SpawnRegion(WORLD, 0, 0, radius, RegionShape.CIRCLE, 0, 320);
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("rejects a negative radius")
        void rejectsNegativeRadius() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new SpawnRegion(WORLD, 0, 0, -1, RegionShape.SQUARE, 0, 320));
        }

        @Test
        @DisplayName("rejects minY greater than maxY")
        void rejectsInvertedYBounds() {
            assertThatIllegalArgumentException()
                    .isThrownBy(
                            () -> new SpawnRegion(WORLD, 0, 0, 16, RegionShape.SQUARE, 100, 10));
        }

        @Test
        @DisplayName("rejects a null world")
        void rejectsNullWorld() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SpawnRegion(null, 0, 0, 16, RegionShape.SQUARE, 0, 320));
        }

        @Test
        @DisplayName("rejects a null shape")
        void rejectsNullShape() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new SpawnRegion(WORLD, 0, 0, 16, null, 0, 320));
        }

        @Test
        @DisplayName("permits equal minY and maxY")
        void permitsFlatYBounds() {
            SpawnRegion region = new SpawnRegion(WORLD, 0, 0, 16, RegionShape.SQUARE, 64, 64);
            assertThat(region.contains(WORLD, 0, 64, 0)).isTrue();
            assertThat(region.contains(WORLD, 0, 65, 0)).isFalse();
        }
    }

    @Nested
    @DisplayName("square footprint")
    class Square {

        @Test
        @DisplayName("contains the centre")
        void containsCentre() {
            assertThat(square(16).contains(WORLD, 0, 64, 0)).isTrue();
        }

        @ParameterizedTest
        @CsvSource({"16, 0", "0, 16", "16, 16", "-16, -16", "16, -16"})
        @DisplayName("includes the full square out to the radius, corners included")
        void includesCornersAtRadius(int x, int z) {
            assertThat(square(16).contains(WORLD, x, 64, z)).isTrue();
        }

        @ParameterizedTest
        @CsvSource({"17, 0", "0, 17", "17, 17", "-17, 0"})
        @DisplayName("excludes columns beyond the radius")
        void excludesBeyondRadius(int x, int z) {
            assertThat(square(16).contains(WORLD, x, 64, z)).isFalse();
        }
    }

    @Nested
    @DisplayName("circle footprint")
    class Circle {

        @Test
        @DisplayName("contains the centre")
        void containsCentre() {
            assertThat(circle(16).contains(WORLD, 0, 64, 0)).isTrue();
        }

        @ParameterizedTest
        @CsvSource({"16, 0", "0, 16", "-16, 0", "11, 11"})
        @DisplayName("includes columns within the Euclidean radius")
        void includesWithinRadius(int x, int z) {
            // 11^2 + 11^2 = 242 <= 256 = 16^2
            assertThat(circle(16).contains(WORLD, x, 64, z)).isTrue();
        }

        @ParameterizedTest
        @CsvSource({"16, 16", "12, 12", "17, 0", "0, 17"})
        @DisplayName("excludes the square corners a circle does not reach, on either axis")
        void excludesOutsideRadius(int x, int z) {
            // 12^2 + 12^2 = 288 > 256; 17,0 and 0,17 exceed the radius on one axis alone.
            assertThat(circle(16).contains(WORLD, x, 64, z)).isFalse();
        }
    }

    @Nested
    @DisplayName("bounds")
    class Bounds {

        @Test
        @DisplayName("excludes blocks below minY and above maxY")
        void excludesOutsideYBounds() {
            SpawnRegion region = new SpawnRegion(WORLD, 0, 0, 16, RegionShape.SQUARE, 60, 70);
            assertThat(region.contains(WORLD, 0, 59, 0)).isFalse();
            assertThat(region.contains(WORLD, 0, 60, 0)).isTrue();
            assertThat(region.contains(WORLD, 0, 70, 0)).isTrue();
            assertThat(region.contains(WORLD, 0, 71, 0)).isFalse();
        }

        @Test
        @DisplayName("excludes blocks in a different world")
        void excludesOtherWorld() {
            assertThat(square(16).contains("nether", 0, 64, 0)).isFalse();
        }

        @Test
        @DisplayName("radius zero protects only the centre column")
        void radiusZeroProtectsSingleColumn() {
            SpawnRegion region = square(0);
            assertThat(region.contains(WORLD, 0, 64, 0)).isTrue();
            assertThat(region.contains(WORLD, 1, 64, 0)).isFalse();
            assertThat(region.contains(WORLD, 0, 64, 1)).isFalse();
        }

        @Test
        @DisplayName("does not overflow for extreme coordinates")
        void handlesExtremeCoordinatesWithoutOverflow() {
            SpawnRegion region =
                    new SpawnRegion(
                            WORLD,
                            Integer.MIN_VALUE,
                            Integer.MIN_VALUE,
                            16,
                            RegionShape.CIRCLE,
                            0,
                            320);
            assertThat(region.contains(WORLD, Integer.MAX_VALUE, 64, Integer.MAX_VALUE)).isFalse();
        }
    }

    @Nested
    @DisplayName("entering from outside")
    class EntersFromOutside {

        @Test
        @DisplayName("a change from outside to inside enters")
        void outsideToInsideEnters() {
            assertThat(square(16).entersFromOutside(WORLD, 17, 64, 0, WORLD, 16, 64, 0)).isTrue();
        }

        @Test
        @DisplayName("a change that starts inside belongs to the region")
        void insideToInsideDoesNotEnter() {
            assertThat(square(16).entersFromOutside(WORLD, 16, 64, 0, WORLD, 15, 64, 0)).isFalse();
        }

        @Test
        @DisplayName("a change that ends outside touches nothing here")
        void endingOutsideDoesNotEnter() {
            assertThat(square(16).entersFromOutside(WORLD, 16, 64, 0, WORLD, 17, 64, 0)).isFalse();
            assertThat(square(16).entersFromOutside(WORLD, 18, 64, 0, WORLD, 17, 64, 0)).isFalse();
        }

        @Test
        @DisplayName("a change from another world, as through a portal, enters")
        void otherWorldToInsideEnters() {
            assertThat(square(16).entersFromOutside("world_the_end", 0, 64, 0, WORLD, 0, 64, 0))
                    .isTrue();
        }

        @Test
        @DisplayName("a change from above or below the vertical bounds enters")
        void outsideVerticalBoundsToInsideEnters() {
            assertThat(square(16).entersFromOutside(WORLD, 0, 321, 0, WORLD, 0, 320, 0)).isTrue();
        }
    }

    @Nested
    @DisplayName("reachedAlong")
    class ReachedAlong {

        @Test
        @DisplayName("a step that ends inside reaches the region")
        void stepEndingInsideReaches() {
            assertThat(square(16).reachedAlong(WORLD, 17.6, 64.5, 0.5, -1.1, 0.1, 0)).isTrue();
        }

        @Test
        @DisplayName("a step that stops short of the boundary does not")
        void stepStoppingShortDoesNotReach() {
            assertThat(square(16).reachedAlong(WORLD, 19.5, 64.5, 0.5, -1.1, 0.1, 0)).isFalse();
        }

        @Test
        @DisplayName("a fast step that only clips a corner of the region reaches it")
        void stepClippingACornerReaches() {
            // From outside the x edge to outside the z edge, crossing the corner at (16, 16).
            assertThat(square(16).reachedAlong(WORLD, 17.9, 64.5, 15.1, -2.5, 0, 2.5)).isTrue();
            assertThat(square(16).reachedAlong(WORLD, 17.9, 64.5, 18.1, -2.5, 0, 2.5)).isFalse();
        }

        @Test
        @DisplayName("a point already inside reaches it without moving")
        void stationaryInsideReaches() {
            assertThat(square(16).reachedAlong(WORLD, 0.5, 64.5, 0.5, 0, 0, 0)).isTrue();
            assertThat(square(16).reachedAlong(WORLD, 20.5, 64.5, 0.5, 0, 0, 0)).isFalse();
        }

        @Test
        @DisplayName("a step in another world never reaches it")
        void otherWorldNeverReaches() {
            assertThat(square(16).reachedAlong("world_nether", 17.6, 64.5, 0.5, -1.1, 0, 0))
                    .isFalse();
        }

        @Test
        @DisplayName("a displacement that is not finite is treated as staying put")
        void nonFiniteDisplacementStaysPut() {
            double nan = Double.NaN;
            double infinity = Double.POSITIVE_INFINITY;
            assertThat(square(16).reachedAlong(WORLD, 17.5, 64.5, 0.5, nan, 0, 0)).isFalse();
            assertThat(square(16).reachedAlong(WORLD, 17.5, 64.5, 0.5, -infinity, 0, 0)).isFalse();
            assertThat(square(16).reachedAlong(WORLD, 0.5, 64.5, 0.5, infinity, 0, 0)).isTrue();
        }

        @Test
        @DisplayName("a step longer than sixteen blocks is still sampled along its whole length")
        void longStepIsSampledToItsEnd() {
            assertThat(square(16).reachedAlong(WORLD, 40.5, 64.5, 0.5, -30, 0, 0)).isTrue();
        }
    }

    @Test
    @DisplayName("centredAt moves the centre and keeps every other bound")
    void centredAtMovesOnlyTheCentre() {
        SpawnRegion region = new SpawnRegion("world", 0, 0, 16, RegionShape.CIRCLE, 60, 90);

        SpawnRegion moved = region.centredAt(1000, -40);

        assertThat(moved)
                .isEqualTo(new SpawnRegion("world", 1000, -40, 16, RegionShape.CIRCLE, 60, 90));
        assertThat(moved.contains("world", 1010, 70, -40)).isTrue();
        assertThat(moved.contains("world", 0, 70, 0)).isFalse();
    }
}
