package com.kcalma.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.kcalma.progress.WeightTrendCalculator.Point;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link WeightTrendCalculator}. No Spring context.
 *
 * Expected values are computed by hand from the documented formula:
 * trend[0] = weight[0]; trend[i] = trend[i-1] + alphaEffective * (weight[i] - trend[i-1]),
 * where alphaEffective = 1 - (1 - alpha)^gapDays and gapDays is the number of calendar days
 * since the previous weigh-in.
 */
class WeightTrendCalculatorTest {

    private final WeightTrendCalculator calculator = new WeightTrendCalculator();

    @Test
    void smooth_emptyInput_returnsEmptyList() {
        assertThat(calculator.smooth(List.of())).isEmpty();
    }

    @Test
    void smooth_singlePoint_trendEqualsTheRawWeight() {
        List<Point> result = calculator.smooth(List.of(new Point(LocalDate.of(2026, 1, 1), 80.0)));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.get(0).weightKg()).isCloseTo(80.0, within(0.0001));
    }

    @Test
    void smooth_consecutiveDays_appliesDailyAlphaDirectly() {
        // gap = 1 day -> alphaEffective = alpha = 0.1
        // trend1 = 80.0 + 0.1*(79.0-80.0) = 79.9
        List<Point> input = List.of(
                new Point(LocalDate.of(2026, 1, 1), 80.0), new Point(LocalDate.of(2026, 1, 2), 79.0));

        List<Point> result = calculator.smooth(input);

        assertThat(result.get(0).weightKg()).isCloseTo(80.0, within(0.0001));
        assertThat(result.get(1).weightKg()).isCloseTo(79.9, within(0.0001));
    }

    @Test
    void smooth_gapOfSevenDays_compoundsAlphaOverTheGap() {
        // gap = 7 days -> alphaEffective = 1 - 0.9^7 = 1 - 0.4782969 = 0.5217031
        // trend1 = 80.0 + 0.5217031*(78.0-80.0) = 78.9565938
        List<Point> input = List.of(
                new Point(LocalDate.of(2026, 1, 1), 80.0), new Point(LocalDate.of(2026, 1, 8), 78.0));

        List<Point> result = calculator.smooth(input);

        assertThat(result.get(1).weightKg()).isCloseTo(78.9565938, within(0.0001));
    }

    @Test
    void smooth_threeConsecutivePoints_chainsUpdatesSequentially() {
        // trend0 = 80.0
        // trend1 = 80.0 + 0.1*(79.9-80.0)   = 79.99
        // trend2 = 79.99 + 0.1*(79.5-79.99) = 79.941
        List<Point> input = List.of(
                new Point(LocalDate.of(2026, 1, 1), 80.0),
                new Point(LocalDate.of(2026, 1, 2), 79.9),
                new Point(LocalDate.of(2026, 1, 3), 79.5));

        List<Point> result = calculator.smooth(input);

        assertThat(result.get(0).weightKg()).isCloseTo(80.0, within(0.0001));
        assertThat(result.get(1).weightKg()).isCloseTo(79.99, within(0.0001));
        assertThat(result.get(2).weightKg()).isCloseTo(79.941, within(0.0001));
    }

    @Test
    void smooth_customAlpha_isHonoredOverTheDefault() {
        // alpha = 0.5, gap = 1 day -> trend1 = 80.0 + 0.5*(70.0-80.0) = 75.0
        WeightTrendCalculator halfAlpha = new WeightTrendCalculator(0.5);
        List<Point> input = List.of(
                new Point(LocalDate.of(2026, 1, 1), 80.0), new Point(LocalDate.of(2026, 1, 2), 70.0));

        List<Point> result = halfAlpha.smooth(input);

        assertThat(result.get(1).weightKg()).isCloseTo(75.0, within(0.0001));
    }

    @Test
    void smooth_preservesInputDatesAndOrder() {
        List<Point> input = List.of(
                new Point(LocalDate.of(2026, 1, 1), 80.0),
                new Point(LocalDate.of(2026, 1, 5), 79.0),
                new Point(LocalDate.of(2026, 1, 20), 78.0));

        List<Point> result = calculator.smooth(input);

        assertThat(result).extracting(Point::date).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 20));
    }
}
