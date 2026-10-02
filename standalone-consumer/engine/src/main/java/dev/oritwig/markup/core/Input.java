/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
package dev.oritwig.markup.core;

import dev.oritwig.markup.platform.Platform;



import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import dev.oritwig.markup.platform.MathAdapter;


import dev.oritwig.markup.support.CubicBezierInterpolator;
import dev.oritwig.markup.support.Size;

import java.util.Vector;

public class Input {
    private final static CubicBezierInterpolator PRESSURE_INTERPOLATOR = new CubicBezierInterpolator(0, 0.5, 0, 1);

    private RenderView renderView;

    private boolean beganDrawing;
    private boolean isFirst;
    private long drawingStart;
    private boolean hasMoved;
    private boolean clearBuffer;

    private Point lastLocation, lastThickLocation;
    private double lastRemainder;
    private boolean lastAngleSet;
    private float lastAngle;
    private float lastScale;
    private boolean canFill;
    private Point[] points = new Point[3];
    private int pointsCount, realPointsCount;
    private double thicknessSum, thicknessCount;

    private ValueAnimator arrowAnimator;
    // Completion is the GL commit callback, not ACTION_UP or animation scheduling.
    private int pendingCommits;
    private int lifecycleGeneration;
    public boolean isIdle() { return !beganDrawing && arrowAnimator == null && pendingCommits == 0; }
    private void committed(int generation) {
        Platform.runOnUIThread(() -> {
            if (generation != lifecycleGeneration) return;
            pendingCommits = Math.max(0, pendingCommits - 1);
            renderView.onInputIdle();
        });
    }
    public void cancelForLifecycle() {
        lifecycleGeneration++;
        if (arrowAnimator != null) {
            arrowAnimator.removeAllListeners(); arrowAnimator.removeAllUpdateListeners();
            arrowAnimator.cancel(); arrowAnimator = null;
        }
        beganDrawing = false; pendingCommits = 0; pointsCount = realPointsCount = 0;
        lastAngleSet = false; thicknessCount = 0; thicknessSum = 0;
        renderView.getPainting().clearStroke();
    }



    


    private Matrix invertMatrix;
    private float[] tempPoint = new float[2];

    private long lastVelocityUpdate;
    private float velocity;

    public Input(RenderView renderView) {
        this.renderView = renderView;

    }

    public void setMatrix(Matrix m) {
        invertMatrix = new Matrix();
        m.invert(invertMatrix);
    }

    private boolean ignore;
    public void ignoreOnce() {
        this.ignore = true;
    }



    public void process(MotionEvent event, float scale) {
        if (arrowAnimator != null || pendingCommits > 0) {
            return;
        }
        int action = event.getActionMasked();
        float x = event.getX();
        float y = renderView.getHeight() - event.getY();

        tempPoint[0] = x;
        tempPoint[1] = y;
        invertMatrix.mapPoints(tempPoint);

        long dt = System.currentTimeMillis() - lastVelocityUpdate;
        velocity = MathAdapter.clamp(velocity - dt / 125f, 0.6f, 1f);
        if (renderView.getCurrentBrush() != null && renderView.getCurrentBrush() instanceof Brush.Arrow) {
            velocity = 1 - velocity;
        }
        lastScale = scale;
        lastVelocityUpdate = System.currentTimeMillis();

        boolean stylusToolPressed = false;
        float weight = velocity;
        if (event.getToolType(event.getActionIndex()) == MotionEvent.TOOL_TYPE_STYLUS) {
            weight = Math.max(.1f, PRESSURE_INTERPOLATOR.getInterpolation(event.getPressure()));
            stylusToolPressed = (event.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY) == MotionEvent.BUTTON_STYLUS_PRIMARY;
        }
        if (renderView.getCurrentBrush() != null) {
            weight = 1 + (weight - 1) * Platform.lerp(renderView.getCurrentBrush().getSmoothThicknessRate(), 1, MathAdapter.clamp(realPointsCount / 16f, 0, 1));
        }
        Point location = new Point(tempPoint[0], tempPoint[1], weight);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                if (ignore) {
                    return;
                }
                if (!beganDrawing) {
                    beganDrawing = true;
                    hasMoved = false;
                    isFirst = true;

                    lastLocation = location;
                    drawingStart = System.currentTimeMillis();

                    points[0] = location;
                    pointsCount = 1;
                    realPointsCount = 1;
                    lastAngleSet = false;

                    clearBuffer = true;
                    canFill = true;

                } else {
                    float distance = location.getDistanceTo(lastLocation);
                    if (distance < Platform.dp(renderView.getContext(), 5.0f) / scale) {
                        return;
                    }
                    if (canFill && (distance > Platform.dp(renderView.getContext(), 6) / scale || pointsCount > 4)) {
                        canFill = false;
                    }

                    if (!hasMoved) {
                        renderView.onBeganDrawing();
                        hasMoved = true;


                    }

                    points[pointsCount] = location;

                    pointsCount++;
                    realPointsCount++;

                    if (pointsCount == 3) {
                        float angle = (float) Math.atan2(points[2].y - points[1].y, points[2].x - points[1].x);
                        if (!lastAngleSet) {
                            lastAngle = angle;
                            lastAngleSet = true;
                        } else {
                            float f = MathAdapter.clamp(distance / (Platform.dp(renderView.getContext(), 16) / scale), 0, 1);
                            if (f > .4f) {
                                lastAngle = lerpAngle(lastAngle, angle, f);
                            }
                        }
                        smoothenAndPaintPoints(false, renderView.getCurrentBrush().getSmoothThicknessRate());
                    }

                    lastLocation = location;
                    if (distance > Platform.dp(renderView.getContext(), 8) / scale) {
                        lastThickLocation = location;
                    }

                    velocity = MathAdapter.clamp(velocity + dt / 75f, 0.6f, 1);
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (ignore) {
                    ignore = false;
                    return;
                }
                canFill = false;

                {

                    boolean commit = true;
                    if (!hasMoved) {
                        if (renderView.shouldDraw()) {
                            location.edge = true;
                            paintPath(new Path(location));
                        }
                        reset();
                    } else if (pointsCount > 0) {
                        smoothenAndPaintPoints(true, renderView.getCurrentBrush().getSmoothThicknessRate());

                        Brush brush = renderView.getCurrentBrush();
                        if (brush instanceof Brush.Arrow) {
                            float angle = lastAngle;
                            final Point loc = points[pointsCount - 1];
                            double z = lastThickLocation == null ? location.z : lastThickLocation.z;
                            float arrowLength = renderView.getCurrentWeight() * (float) z * 12f;

                            commit = false;
                            if (arrowAnimator != null) {
                                arrowAnimator.cancel();
                            }
                            final float[] lastT = new float[1];
                            final boolean[] vibrated = new boolean[1];
                            arrowAnimator = ValueAnimator.ofFloat(0, 1);
                            arrowAnimator.addUpdateListener(anm -> {
                                float t = (float) anm.getAnimatedValue();

                                double leftCos = Math.cos(angle - Math.PI / 4 * 3.3);
                                double leftSin = Math.sin(angle - Math.PI / 4 * 3.5);
                                paintPath(new Path(new Point[]{
                                    new Point(loc.x + leftCos * arrowLength * lastT[0], loc.y + leftSin * arrowLength * lastT[0], z),
                                    new Point(loc.x + leftCos * arrowLength * t, loc.y + leftSin * arrowLength * t, z, true)
                                }));
                                double rightCos = Math.cos(angle + Math.PI / 4 * 3.3);
                                double rightSin = Math.sin(angle + Math.PI / 4 * 3.5);
                                paintPath(new Path(new Point[]{
                                    new Point(loc.x + rightCos * arrowLength * lastT[0], loc.y + rightSin * arrowLength * lastT[0], z),
                                    new Point(loc.x + rightCos * arrowLength * t, loc.y + rightSin * arrowLength * t, z, true)
                                }));

                                if (!vibrated[0] && t > .4f) {
                                    vibrated[0] = true;
                                }

                                lastT[0] = t;
                            });
                            arrowAnimator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    pendingCommits++;
                                    final int generation = lifecycleGeneration;
                                    renderView.getPainting().commitPath(null, renderView.getCurrentColor(), true, () -> committed(generation));
                                    arrowAnimator = null;
                                }
                            });
                            arrowAnimator.setDuration(240);
                            arrowAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                            arrowAnimator.start();
                        }
                    }

                    if (commit) {
                        pendingCommits++;
                        final int generation = lifecycleGeneration;
                        renderView.getPainting().commitPath(null, renderView.getCurrentColor(), true, () -> committed(generation));
                    }
                }

                pointsCount = 0;
                realPointsCount = 0;
                lastAngleSet = false;
                beganDrawing = false;
                thicknessCount = thicknessSum = 0;

                renderView.onFinishedDrawing(hasMoved);
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                if (ignore) {
                    ignore = false;
                    return;
                }
                canFill = false;


                pendingCommits++;
                final int generation = lifecycleGeneration;
                renderView.getPainting().clearStroke(() -> committed(generation));
                pointsCount = 0;
                realPointsCount = 0;
                lastAngleSet = false;
                beganDrawing = false;
                thicknessCount = thicknessSum = 0;


                break;
            }
        }
    }

    private float lerpAngle(float angleA, float angleB, float t) {
//        double da = (angleB - angleA) % (Math.PI * 2);
//        return (float) (angleA + (2 * da % (Math.PI * 2) - da) * t);
        return (float) Math.atan2((1-t)*Math.sin(angleA) + t*Math.sin(angleB), (1-t)*Math.cos(angleA) + t*Math.cos(angleB));
    }

    private void reset() {
        pointsCount = 0;
    }

    private void smoothenAndPaintPoints(boolean ended, float smoothThickness) {
        if (pointsCount > 2) {
            Vector<Point> points = new Vector<>();

            Point prev2 = this.points[0];
            Point prev1 = this.points[1];
            Point cur = this.points[2];

            if (cur == null || prev1 == null || prev2 == null) {
                return;
            }

            Point midPoint1 = prev1.multiplySum(prev2, 0.5f);
            Point midPoint2 = cur.multiplySum(prev1, 0.5f);

            int segmentDistance = 1;
            float distance = midPoint1.getDistanceTo(midPoint2);
            int numberOfSegments = (int) Math.min(48, Math.max(Math.floor(distance / segmentDistance), 24));

            float t = 0.0f;
            float step = 1.0f / (float) numberOfSegments;

            for (int j = 0; j < numberOfSegments; j++) {
                Point point = smoothPoint(midPoint1, midPoint2, prev1, t, smoothThickness);
                if (isFirst) {
                    point.edge = true;
                    isFirst = false;
                }
                points.add(point);
                thicknessSum += point.z;
                thicknessCount++;
                t += step;
            }

            if (ended) {
                midPoint2.edge = true;
            }
            points.add(midPoint2);

            Point[] result = new Point[points.size()];
            points.toArray(result);

            Path path = new Path(result);
            paintPath(path);

            System.arraycopy(this.points, 1, this.points, 0, 2);

            if (ended) {
                pointsCount = 0;
            } else {
                pointsCount = 2;
            }
        } else {
            Point[] result = new Point[pointsCount];
            System.arraycopy(this.points, 0, result, 0, pointsCount);
            Path path = new Path(result);
            paintPath(path);
        }
    }

    private Point smoothPoint(Point midPoint1, Point midPoint2, Point prev1, float t, float smoothThickness) {
        double a1 = Math.pow(1.0f - t, 2);
        double a2 = (2.0f * (1.0f - t) * t);
        double a3 = t * t;

        float t_squared = t * t;
        float minus_t_squard = (1 - t) * (1 - t);

        double x = midPoint1.x * minus_t_squard + 2 * prev1.x * t * (1 - t) + midPoint2.x * t_squared;
        double y = midPoint1.y * minus_t_squard + 2 * prev1.y * t * (1 - t) + midPoint2.y * t_squared;
        double z = midPoint1.z * a1 + prev1.z * a2 + midPoint2.z * a3;
        z = 1 + (z - 1) * Platform.lerp(smoothThickness, 1, MathAdapter.clamp(realPointsCount / 16f, 0, 1));

        return new Point(x, y, z);
    }

    private void paintPath(final Path path) {
        path.setup(renderView.getCurrentColor(), renderView.getCurrentWeight(), renderView.getCurrentBrush());

        if (clearBuffer) {
            lastRemainder = 0.0f;
        }

        path.remainder = lastRemainder;

        renderView.getPainting().paintStroke(path, clearBuffer, false, () -> Platform.runOnUIThread(() -> lastRemainder = path.remainder));
        clearBuffer = false;
    }
}
