package com.kcalma.progress;

import java.util.List;

/**
 * Pure domain math: the ordinary-least-squares (OLS) slope of a best-fit line through a set of
 * (x, y) points — the textbook formula a spreadsheet trendline uses. No Spring, no I/O — safe to
 * unit test exhaustively. Used by {@link ProgressService} to turn the smoothed weight trend into
 * a "kg per day" rate over a recent window.
 */
public final class LinearRegression {

    /**
     * @param points at least 2 points; x values must not be all identical (a vertical/undefined
     *     slope)
     * @return the slope (change in y per unit of x) of the best-fit line through the points
     * @throws IllegalArgumentException if fewer than 2 points are given, or every point shares the
     *     same x
     */
    public double slope(List<Point> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("At least 2 points are required to compute a slope.");
        }

        double meanX = points.stream().mapToDouble(Point::x).average().orElseThrow();
        double meanY = points.stream().mapToDouble(Point::y).average().orElseThrow();

        double numerator = 0;
        double denominator = 0;
        for (Point point : points) {
            double dx = point.x() - meanX;
            numerator += dx * (point.y() - meanY);
            denominator += dx * dx;
        }

        if (denominator == 0) {
            throw new IllegalArgumentException("All points share the same x; slope is undefined.");
        }

        return numerator / denominator;
    }

    public record Point(double x, double y) {}
}
