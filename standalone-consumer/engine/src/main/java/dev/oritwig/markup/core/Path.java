/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
package dev.oritwig.markup.core;

import dev.oritwig.markup.platform.Platform;

import java.util.Arrays;
import java.util.Vector;

public class Path {

    public double remainder;
    private Vector<Point> points = new Vector<>();
    private int color;
    private float baseWeight;
    private Brush brush;

    public Path(Point point) {
        points.add(point);
    }

    public Path(Point[] points) {
        this.points.addAll(Arrays.asList(points));
    }

    public int getLength() {
        if (points == null) {
            return 0;
        }
        return points.size();
    }

    public Point[] getPoints() {
        Point[] points = new Point[this.points.size()];
        this.points.toArray(points);
        return points;
    }

    public int getColor() {
        return color;
    }

    public float getBaseWeight() {
        return baseWeight;
    }

    public Brush getBrush() {
        return brush;
    }

    public void setup(int color, float baseWeight, Brush brush) {
        this.color = color;
        this.baseWeight = baseWeight;
        this.brush = brush;
    }
}
