package com.kcalma.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.kcalma.progress.LinearRegression.Point;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure unit tests for {@link LinearRegression}. No Spring context. */
class LinearRegressionTest {

    private final LinearRegression regression = new LinearRegression();

    @Test
    void slope_perfectLine_returnsExactSlope() {
        // y = 2x + 10
        List<Point> points = List.of(new Point(0, 10), new Point(1, 12), new Point(2, 14), new Point(3, 16));

        assertThat(regression.slope(points)).isCloseTo(2.0, within(0.0001));
    }

    @Test
    void slope_decreasingLine_returnsNegativeSlope() {
        List<Point> points = List.of(new Point(0, 80), new Point(1, 79), new Point(2, 78));

        assertThat(regression.slope(points)).isCloseTo(-1.0, within(0.0001));
    }

    @Test
    void slope_noisyPoints_matchesHandComputedOls() {
        // x=[0,1,2,3,4], y=[2,3,5,4,6] -> meanX=2, meanY=4
        // numerator = sum(dx*dy) = 4+1+0+0+4 = 9; denominator = sum(dx^2) = 4+1+0+1+4 = 10
        // slope = 9/10 = 0.9
        List<Point> points = List.of(new Point(0, 2), new Point(1, 3), new Point(2, 5), new Point(3, 4), new Point(4, 6));

        assertThat(regression.slope(points)).isCloseTo(0.9, within(0.0001));
    }

    @Test
    void slope_fewerThanTwoPoints_throws() {
        assertThatThrownBy(() -> regression.slope(List.of(new Point(0, 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> regression.slope(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slope_allPointsShareTheSameX_throws() {
        assertThatThrownBy(() -> regression.slope(List.of(new Point(5, 1), new Point(5, 2))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
