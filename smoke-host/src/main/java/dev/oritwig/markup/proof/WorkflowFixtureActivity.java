package dev.oritwig.markup.proof;

import android.app.Activity;
import android.animation.Animator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import dev.oritwig.markup.api.MarkupSession;
import dev.oritwig.markup.core.RenderView;
import dev.oritwig.markup.core.views.EntityView;
import dev.oritwig.markup.core.views.TextPaintView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Bounded, explicitly launched regression fixture, separate from the ordinary host.
 * No sleeps stand in for drawing completion: isIdle/whenIdle observe the GL commit.
 * Gesture events enter Activity.dispatchTouchEvent, never EntityView.onTouchEvent.
 * Reflection below ONLY observes retained handler classification/state; it never
 * invokes a handler or changes engine fields. Pixel comparisons have zero tolerance.
 * This is synthetic API/device evidence, not a physical stylus or product UX test.
 */
public final class WorkflowFixtureActivity extends Activity {
    private static final String TAG = "PaintWorkflow";
    private static final int WIDTH = 320, HEIGHT = 240;
    private static final long RUN_LIMIT_MS = 300000, WAIT_LIMIT_MS = 30000;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final JSONObject report = new JSONObject();
    private final JSONArray checks = new JSONArray(), cases = new JSONArray();
    private final Map<String, Integer> touched = new HashMap<>();
    private FrameLayout root;
    private TextView status;
    private MarkupSession session;
    private Bitmap input, inputBefore;
    private File output, cacheRoot;
    private long deadline, cleanupDeadline;
    private boolean cleanupMode;
    private int failures, passed, skipped, imageNumber;
    private String caseName;
    private volatile boolean destroyed;

    private interface Task<T> { T call() throws Exception; }
    private interface Case { void run() throws Exception; }

    private long remaining(long cap) {
        long value = Math.min(cap, (cleanupMode ? cleanupDeadline : deadline) - SystemClock.uptimeMillis());
        if (value <= 0) throw new AssertionError(cleanupMode ? "Fixture cleanup exceeded its separate ten-second bound" : "Fixture exceeded its five-minute bound");
        return value;
    }

    private <T> T ui(Task<T> task) throws Exception {
        if (destroyed) throw new AssertionError("Fixture Activity was destroyed");
        FutureTask<T> future = new FutureTask<>(task::call);
        runOnUiThread(future);
        return future.get(remaining(WAIT_LIMIT_MS), TimeUnit.MILLISECONDS);
    }

    private void check(String name, boolean pass) throws Exception {
        checks.put(new JSONObject().put("case", caseName).put("name", name)
                .put("status", pass ? "pass" : "fail").put("pass", pass));
        if (pass) passed++; else failures++;
    }

    private void require(String name, boolean pass) throws Exception {
        check(name, pass);
        if (!pass) throw new AssertionError(name);
    }

    private void skip(String name, String reason) throws Exception {
        checks.put(new JSONObject().put("case", caseName).put("name", name)
                .put("status", "unsupported-not-covered").put("reason", reason));
        skipped++;
    }

    /** Bounded polling is only for observable layout/surface/lifecycle state. */
    private void awaitState(String name, Task<Boolean> predicate) throws Exception {
        long end = SystemClock.uptimeMillis() + remaining(WAIT_LIMIT_MS);
        while (!ui(predicate)) {
            if (SystemClock.uptimeMillis() >= end) throw new AssertionError(name + " timed out");
            CountDownLatch frame = new CountDownLatch(1);
            mainHandler.postDelayed(frame::countDown, 16);
            if (!frame.await(remaining(WAIT_LIMIT_MS), TimeUnit.MILLISECONDS))
                throw new AssertionError(name + " UI frame timed out");
        }
    }

    private void idle() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        ui(() -> { session.whenIdle(done::countDown); return null; });
        if (!done.await(remaining(WAIT_LIMIT_MS), TimeUnit.MILLISECONDS))
            throw new AssertionError("No actual idle/GL commit callback");
        if (!ui(() -> session.isIdle())) throw new AssertionError("Idle callback was premature");
    }

    private void laidOut(View view) throws Exception {
        awaitState("view layout", () -> view.getWidth() > 0 && view.getHeight() > 0
                && !view.isLayoutRequested() && !root.isLayoutRequested());
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        deadline = SystemClock.uptimeMillis() + RUN_LIMIT_MS;
        String run = getIntent().getStringExtra("run_id");
        if (run == null) run = "manual-" + System.currentTimeMillis();
        if (!run.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Bad run id");
        output = new File(getExternalFilesDir(null), "workflow-proof/" + run);
        cacheRoot = new File(getCacheDir(), "workflow-proof-" + run);
        if ((!output.mkdirs() && !output.isDirectory()) || (!cacheRoot.mkdirs() && !cacheRoot.isDirectory()))
            throw new IllegalStateException("Cannot create fixture directories");
        root = new FrameLayout(this);
        root.setBackgroundColor(0xffdddddd);
        status = new TextView(this);
        status.setText("Paint workflow regression fixture running");
        root.addView(status, new FrameLayout.LayoutParams(-1, 48));
        setContentView(root);
        new Thread(() -> {
            try {
                runProof();
                report.put("status", failures == 0 ? "pass-with-declared-coverage-limits" : "observed-failures");
            } catch (Throwable error) {
                failures++;
                try { report.put("status", "error").put("error", error.toString()); } catch (Exception ignored) { }
                android.util.Log.e(TAG, "fixture failed", error);
            } finally {
                cleanupMode = true;
                cleanupDeadline = SystemClock.uptimeMillis() + 10000;
                try {
                    try { dispose(); } catch (Throwable error) {
                        failures++;
                        try { report.put("cleanupError", error.toString()).put("status", "error"); } catch (Exception ignored) { }
                    }
                } finally {
                try {
                    report.put("checks", checks).put("cases", cases).put("failures", failures)
                            .put("passed", passed).put("unsupportedNotCovered", skipped)
                            .put("sdk", Build.VERSION.SDK_INT).put("manufacturer", Build.MANUFACTURER)
                            .put("model", Build.MODEL).put("syntheticGestureDispatch", true)
                            .put("physicalStylus", false).put("eventEntry", "Activity.dispatchTouchEvent")
                            .put("rawCoordinateOrigin", "sourceToScreen plus measured decor location")
                            .put("pixelTolerance", 0).put("arbitraryStrokeSettleDelayMs", 0)
                            .put("ordinaryHostReopen", "Separate ordinary-host test; not asserted by this fixture");
                    try (FileWriter writer = new FileWriter(new File(output, "result.json"))) {
                        writer.write(report.toString(2));
                    }
                    android.util.Log.i(TAG, report.toString());
                    mainHandler.post(() -> status.setText("Fixture complete: " + passed + " passed, "
                            + failures + " failed, " + skipped + " unsupported/not covered"));
                } catch (Throwable error) { android.util.Log.e(TAG, "report failed", error); }
                }
            }
        }, "PaintWorkflowFixture").start();
    }

    private void runCase(String name, Case task) throws Exception {
        caseName = name;
        int previous = failures, previousPassed = passed, previousSkipped = skipped;
        JSONObject result = new JSONObject().put("name", name);
        long start = SystemClock.uptimeMillis();
        try {
            task.run();
            result.put("status", failures != previous ? "fail"
                    : skipped > previousSkipped && passed == previousPassed ? "unsupported-not-covered" : "pass");
        } catch (Throwable error) {
            failures++;
            result.put("status", "error").put("error", error.toString());
            android.util.Log.e(TAG, name, error);
        } finally {
            result.put("elapsedMs", SystemClock.uptimeMillis() - start);
            cases.put(result);
        }
        remaining(1);
    }

    private static Bitmap source() {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(0xff20aa40); canvas.drawRect(0, 0, 20, 20, paint);
        paint.setColor(0xffe5af00); canvas.drawRect(300, 220, 320, 240, paint);
        return bitmap;
    }

    private void fresh(float scale) throws Exception {
        dispose();
        ui(() -> {
            input = source(); inputBefore = input.copy(Bitmap.Config.ARGB_8888, false);
            session = new MarkupSession(this, input, cacheRoot);
            session.setPivotX(0); session.setPivotY(0);
            session.setScaleX(scale); session.setScaleY(scale);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(WIDTH, HEIGHT);
            params.leftMargin = 36; params.topMargin = 60;
            root.addView(session, params);
            return null;
        });
        awaitState("surface ready", () -> session.isReady());
        laidOut(session);
        idle();
        float[] mapped = ui(() -> {
            float[] screen = new float[2], roundTrip = new float[2];
            session.sourceToScreen(73.25f, 119.5f, screen);
            session.screenToSource(screen[0], screen[1], roundTrip);
            return roundTrip;
        });
        check("coordinate round trip at host scale " + scale,
                Math.abs(mapped[0] - 73.25f) < .001f && Math.abs(mapped[1] - 119.5f) < .001f);
    }

    private void dispose() throws Exception {
        if (session == null) return;
        MarkupSession old = session;
        Bitmap oldInput = input, original = inputBefore;
        if (!destroyed) {
            ui(() -> { old.close(); return null; });
            awaitState("closed renderer cleanup", () -> old.getChildCount() == 0);
            check("caller input remains byte-exact after close", diff(oldInput, original) == 0);
            ui(() -> { root.removeView(old); return null; });
        }
        session = null; input = null; inputBefore = null;
        oldInput.recycle(); original.recycle();
    }

    private Bitmap snap(String name) throws Exception {
        idle();
        Bitmap bitmap = ui(() -> session.exportBitmap());
        save(bitmap, name);
        return bitmap;
    }

    private File save(Bitmap bitmap, String name) throws Exception {
        File file = new File(output, String.format(java.util.Locale.ROOT, "%03d-%s.png", imageNumber++, name));
        try (FileOutputStream stream = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new AssertionError("PNG encode failed");
        }
        return file;
    }

    private static int diff(Bitmap a, Bitmap b) {
        if (a == null || b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return Integer.MAX_VALUE;
        return changed(a, b, 0, 0, a.getWidth(), a.getHeight());
    }

    private static int changed(Bitmap a, Bitmap b, int x0, int y0, int x1, int y1) {
        int count = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++)
            if (a.getPixel(x, y) != b.getPixel(x, y)) count++;
        return count;
    }

    private JSONObject pixelDifference(Bitmap before, Bitmap after) throws Exception {
        JSONObject result = new JSONObject().put("changedPixels", diff(before, after));
        for (int y = 0; y < before.getHeight(); y++) for (int x = 0; x < before.getWidth(); x++) {
            if (before.getPixel(x, y) != after.getPixel(x, y)) {
                return result.put("firstX", x).put("firstY", y)
                        .put("beforeARGB", String.format("%08x", before.getPixel(x, y)))
                        .put("afterARGB", String.format("%08x", after.getPixel(x, y)));
            }
        }
        return result;
    }

    private void undo() throws Exception {
        idle();
        ui(() -> { session.undo(); return null; });
        idle();
        // A synchronous export queues its read behind region Slice restoration.
    }

    /** Called on UI. Obtain with screen coordinates, then offset LOCAL coordinates.
     * MotionEvent retains raw screen coordinates across offsetLocation and child transforms. */
    private boolean dispatch(long down, long time, int action, float[][] points,
                             float pressure, boolean stylus) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[points.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[points.length];
        float[] firstScreen = new float[2];
        for (int i = 0; i < points.length; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = stylus ? MotionEvent.TOOL_TYPE_STYLUS : MotionEvent.TOOL_TYPE_FINGER;
            float[] screen = new float[2];
            session.sourceToScreen(points[i][0], points[i][1], screen);
            if (i == 0) { firstScreen[0] = screen[0]; firstScreen[1] = screen[1]; }
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = screen[0]; coords[i].y = screen[1];
            coords[i].pressure = pressure; coords[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(down, time, action, points.length, properties, coords,
                0, 0, 1, 1, 0, 0, stylus ? InputDevice.SOURCE_STYLUS : InputDevice.SOURCE_TOUCHSCREEN, 0);
        int[] decor = new int[2];
        getWindow().getDecorView().getLocationOnScreen(decor);
        event.offsetLocation(-decor[0], -decor[1]);
        if (Math.abs(event.getRawX() - firstScreen[0]) > .001f || Math.abs(event.getRawY() - firstScreen[1]) > .001f
                || Math.abs(event.getX() + decor[0] - firstScreen[0]) > .001f
                || Math.abs(event.getY() + decor[1] - firstScreen[1]) > .001f) {
            event.recycle(); throw new AssertionError("Synthetic raw/local coordinates disagree");
        }
        try { return WorkflowFixtureActivity.this.dispatchTouchEvent(event); }
        finally { event.recycle(); }
    }

    private boolean dispatch(long down, int index, int action, float x, float y, float pressure, boolean stylus) {
        return dispatch(down, down + index * 16L, action, new float[][]{{x, y}}, pressure, stylus);
    }

    /** All events, including ACTION_UP and immediate finish/rejection probes, share one UI turn. */
    private void path(boolean arrow, int color, float x0, float y0, float x1, float y1,
                      boolean curved, boolean varying, float pressure, boolean cancel, Case afterUp) throws Exception {
        idle();
        ui(() -> {
            session.selectDrawingTool(arrow ? MarkupSession.Tool.STROKE_ARROW : MarkupSession.Tool.PEN);
            session.setColor(color); session.setWeight(.45f);
            long down = SystemClock.uptimeMillis();
            int steps = curved || varying ? 40 : 16;
            for (int i = 0; i <= steps; i++) {
                float fraction = i / (float) steps;
                float x = x0 + (x1 - x0) * fraction;
                float y = curved ? y0 - 100 * (float) Math.sin(Math.PI * fraction) : y0 + (y1 - y0) * fraction;
                if (!dispatch(down, i, i == 0 ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_MOVE,
                        x, y, varying ? .12f + .83f * fraction : pressure, true))
                    throw new AssertionError("Activity did not handle stroke event " + i);
            }
            dispatch(down, steps + 1, cancel ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP, x1, y1, pressure, true);
            if (afterUp != null) afterUp.run();
            return null;
        });
    }

    private void path(boolean arrow, int color, float x0, float y0, float x1, float y1,
                      float pressure, boolean cancel) throws Exception {
        path(arrow, color, x0, y0, x1, y1, false, false, pressure, cancel, null);
        idle();
    }

    private void runProof() throws Exception {
        runCase("source-bounds", this::sourceBounds);
        runCase("retained-pen-pressure-arrow-alpha-cancel", this::retainedDrawing);
        runCase("varying-pressure-curved-arrow", this::curvedDrawing);
        runCase("text-edit-and-programmatic-undo", this::textEditing);
        runCase("immediate-arrow-finish", this::immediateFinish);
        runCase("close-pending-finish", this::closePendingFinish);
        for (float scale : new float[]{1f, .5f}) {
            for (String route : new String[]{"body", "selected-body", "left-handle", "right-handle", "container"}) {
                final float hostScale = scale;
                runCase(route + "-" + scale, () -> entityRoute(route, hostScale));
            }
        }
        runCase("whole-selection-handle-coverage", () -> skip("SELECTION_WHOLE_HANDLE on TextPaintView",
                "Retained TextPaintView returns only left/right handles or 0. No whole-handle hit region exists; not synthesized or counted as a pass."));
        if (Build.VERSION.SDK_INT < 29) {
            runCase("two-pointer-coverage", () -> skip("two-pointer scale/rotation",
                    "Retained EntityView disables secondary raw-pointer mapping below API 29. API " + Build.VERSION.SDK_INT + " is explicitly unsupported."));
        } else {
            runCase("two-pointer-1.0", () -> twoPointer(1f));
            runCase("two-pointer-0.5", () -> twoPointer(.5f));
        }
        runCase("strict-three-cycle-recreation", this::recreation);
        runCase("export-contract", this::exportContract);
        dispose();
        caseName = "cleanup";
        awaitState("owned cache cleanup", () -> {
            File[] files = cacheRoot.listFiles(); return files != null && files.length == 0;
        });
        check("close deletes only fixture-owned session caches", cacheRoot.listFiles().length == 0);
        check("manifest has no requested permissions", getPackageManager().getPackageInfo(getPackageName(),
                android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions == null);
    }

    private void sourceBounds() throws Exception {
        for (int[] size : new int[][]{{2049, 1}, {1, 2049}}) {
            boolean rejected = ui(() -> {
                Bitmap oversized = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
                MarkupSession unexpected = null;
                try { unexpected = new MarkupSession(this, oversized, cacheRoot); return false; }
                catch (IllegalArgumentException expected) { return true; }
                finally { if (unexpected != null) unexpected.close(); oversized.recycle(); }
            });
            check("explicitly reject " + size[0] + "x" + size[1] + " without resizing", rejected);
        }
    }

    private void retainedDrawing() throws Exception {
        fresh(1f);
        Bitmap baseline = snap("baseline");
        check("source exact before annotation", diff(input, baseline) == 0);
        check("source orientation corners", baseline.getPixel(5, 5) == 0xff20aa40 && baseline.getPixel(310, 230) == 0xffe5af00);
        path(false, Color.RED, 25, 55, 295, 55, .9f, false);
        Bitmap pen = snap("pen");
        check("genuine Input pen creates pixels", diff(baseline, pen) > 500);
        check("pen stays in expected band", changed(baseline, pen, 0, 110, 320, 220) == 0);
        check("real undo registered", ui(() -> session.canUndo()));
        undo(); check("region Slice undo restores exact bytes", diff(baseline, snap("pen-undone")) == 0);
        path(false, Color.RED, 30, 80, 135, 80, .12f, false);
        path(false, Color.RED, 185, 80, 290, 80, .95f, false);
        Bitmap pressure = snap("pressure");
        int thin = changed(baseline, pressure, 45, 50, 120, 110), thick = changed(baseline, pressure, 200, 50, 275, 110);
        report.put("pressureThinPixels", thin).put("pressureThickPixels", thick);
        check("retained stylus pressure affects width", thin > 0 && thick > thin * 1.2);
        undo(); undo();
        path(true, 0xffff9600, 35, 155, 270, 155, .9f, false);
        Bitmap arrow = snap("stroke-arrow");
        check("stroke arrow upper arm", changed(baseline, arrow, 190, 110, 275, 145) > 25);
        check("stroke arrow lower arm", changed(baseline, arrow, 190, 165, 275, 200) > 25);
        undo(); check("arrow region undo exact", diff(baseline, snap("arrow-undone")) == 0);
        path(false, 0x800000ff, 30, 115, 290, 115, .9f, false);
        Bitmap alpha = snap("alpha"); int middle = alpha.getPixel(160, 115);
        report.put("alphaPixel", Integer.toHexString(middle));
        check("upstream alpha composition", Color.red(middle) > 80 && Color.red(middle) < 220 && Color.blue(middle) > 240);
        undo();
        path(false, Color.RED, 30, 115, 280, 115, .9f, true);
        check("ACTION_CANCEL leaves pixels unchanged", diff(baseline, snap("stroke-cancel")) == 0);
        check("ACTION_CANCEL creates no undo", !ui(() -> session.canUndo()));
    }

    private void curvedDrawing() throws Exception {
        fresh(1f); Bitmap baseline = snap("curve-baseline");
        path(false, Color.RED, 35, 110, 285, 110, false, true, .9f, false, null);
        Bitmap pressure = snap("varying-pressure");
        int low = changed(baseline, pressure, 60, 75, 105, 145), high = changed(baseline, pressure, 215, 75, 260, 145);
        report.put("pressureLowPixels", low).put("pressureHighPixels", high);
        check("single stroke pressure varies coverage", low > 0 && high > low * 1.15);
        undo(); check("normal stroke undo exact", diff(baseline, snap("varying-pressure-undo")) == 0);
        path(false, Color.RED, 35, 160, 285, 160, true, false, .9f, false, null);
        Bitmap curve = snap("curved-pen"); undo();
        path(true, Color.RED, 35, 160, 285, 160, true, false, .9f, false, null);
        Bitmap arrow = snap("curved-arrow");
        check("curved arrow follows bowed path", changed(baseline, arrow, 115, 40, 195, 95) > 100);
        check("curve differs from start-end chord", changed(baseline, arrow, 100, 145, 220, 175) == 0);
        report.put("arrowComparedWithPenChangedPixels", diff(curve, arrow));
        check("curved arrow adds endpoint arms", diff(curve, arrow) > 100);
        undo(); check("curved arrow is one exact undo", diff(baseline, snap("curved-arrow-undo")) == 0);
    }

    private void textEditing() throws Exception {
        fresh(1f); Bitmap baseline = snap("text-baseline");
        TextPaintView text = ui(() -> session.addText("Café • 東京\nمرحبا", 155, 120, 22, 0xff102090));
        laidOut(text); Bitmap plain = snap("unicode-text");
        check("plain Unicode text is flattened", diff(baseline, plain) > 120);
        check("text has platform multiline layout", ui(() -> text.getEditText().getLineCount()) >= 2);
        ui(() -> { session.transformText(text, 23, -16, 1.25f, 27); return null; });
        Bitmap transformed = snap("transformed-text");
        check("retained entity pan scale rotate changes pixels", diff(plain, transformed) > 120);
        check("entity scale retained", Math.abs(ui(() -> text.getScale()) - 1.25f) < .01f);
        check("entity rotation retained", Math.abs(ui(() -> text.getRotation()) - 27) < .01f);
        undo(); check("text transform undo restores rendered result", diff(plain, snap("text-transform-undone")) == 0);
        ui(() -> { session.select(text); session.editSelectedText("Edited label\nsecond line"); return null; });
        laidOut(text);
        check("edit selected text changes value", "Edited label\nsecond line".equals(ui(() -> session.selectedTextValue())));
        check("edit selected text changes pixels", diff(plain, snap("text-edited")) > 50);
        undo(); laidOut(text);
        check("text edit undo restores exact value", "Café • 東京\nمرحبا".equals(ui(() -> text.getText().toString())));
        check("text edit undo restores exact pixels", diff(plain, snap("text-edit-undone")) == 0);
        undo(); check("text insertion undo restores baseline", diff(baseline, snap("text-undone")) == 0);
    }

    private final class ExportProbe implements MarkupSession.ExportCallback {
        final CountDownLatch done = new CountDownLatch(1);
        volatile int exports, failures;
        volatile Bitmap bitmap;
        volatile MarkupSession.Failure failure;
        volatile boolean callbackOnMain, callbackIdle;
        @Override public void onExport(Bitmap result) {
            exports++; bitmap = result;
            callbackOnMain = Looper.myLooper() == Looper.getMainLooper();
            callbackIdle = session.isIdle(); done.countDown();
        }
        @Override public void onFailure(MarkupSession.Failure reason) {
            failures++; failure = reason;
            callbackOnMain = Looper.myLooper() == Looper.getMainLooper(); done.countDown();
        }
        void await() throws Exception {
            if (!done.await(remaining(WAIT_LIMIT_MS), TimeUnit.MILLISECONDS)) throw new AssertionError("Finish callback timed out");
        }
    }

    private void immediateFinish() throws Exception {
        fresh(1f); Bitmap baseline = snap("immediate-baseline");
        path(true, 0xffff9600, 35, 155, 270, 155, .9f, false);
        Bitmap completedReference = snap("arrow-completed-reference"); undo();
        ExportProbe export = new ExportProbe(), duplicate = new ExportProbe();
        path(true, 0xffff9600, 35, 155, 270, 155, false, false, .9f, false, () -> {
            check("arrow is busy immediately after ACTION_UP", !session.isIdle());
            boolean exportRejected = false, undoRejected = false;
            try { Bitmap unexpected = session.exportBitmap(); unexpected.recycle(); }
            catch (IllegalStateException expected) { exportRejected = true; }
            try { session.undo(); } catch (IllegalStateException expected) { undoRejected = true; }
            check("immediate synchronous busy export rejects", exportRejected);
            check("immediate busy undo rejects", undoRejected);
            session.finish(export);
            check("immediate finish defers callback until commit", export.exports == 0 && export.failures == 0);
            session.finish(duplicate);
            check("second pending finish rejects exactly once", duplicate.exports == 0 && duplicate.failures == 1
                    && duplicate.failure == MarkupSession.Failure.EXPORT_IN_PROGRESS);
        });
        export.await();
        check("finish exports exactly once", export.exports == 1 && export.failures == 0);
        check("finish callback is main-thread and actually idle", export.callbackOnMain && export.callbackIdle);
        require("finish provided bitmap", export.bitmap != null);
        save(export.bitmap, "immediate-finish");
        Bitmap sameStrokeIdle = snap("immediate-same-stroke-idle");
        check("immediate finish matches SAME committed stroke exactly", diff(sameStrokeIdle, export.bitmap) == 0);
        report.put("separateArrowReferenceDiagnosticPixelDifference", diff(completedReference, export.bitmap));
        check("immediate finish includes upper arm", changed(baseline, export.bitmap, 190, 110, 275, 145) > 25);
        check("immediate finish includes lower arm", changed(baseline, export.bitmap, 190, 165, 275, 200) > 25);
        undo(); check("finished arrow still has one exact undo", diff(baseline, snap("finished-arrow-undo")) == 0);
        check("finished arrow undo leaves no extra undo", !ui(() -> session.canUndo()));
        check("finish callback remains exactly once after undo", export.exports + export.failures == 1);
    }

    private void closePendingFinish() throws Exception {
        fresh(1f); ExportProbe export = new ExportProbe();
        path(true, Color.RED, 35, 155, 270, 155, false, false, .9f, false, () -> {
            session.finish(export);
            check("close test starts with pending finish", export.exports == 0 && export.failures == 0);
            session.close(); session.close();
            check("close fails pending finish exactly once", export.exports == 0 && export.failures == 1
                    && export.failure == MarkupSession.Failure.SESSION_CLOSED);
        });
        export.await();
        awaitState("close completes GL shutdown", () -> session.getChildCount() == 0);
        ui(() -> null); // Drain main callbacks queued by completed GL shutdown.
        check("closed finish never produces a late export", export.exports == 0 && export.failures == 1);
        check("closed finish failure is main-thread", export.callbackOnMain);
    }

    private static final class Geometry {
        final float x, y, scale, rotation;
        Geometry(TextPaintView view) {
            x = view.getPosition().x; y = view.getPosition().y;
            scale = view.getScale(); rotation = view.getRotation();
        }
        boolean exact(TextPaintView view) {
            return Float.compare(x, view.getPosition().x) == 0 && Float.compare(y, view.getPosition().y) == 0
                    && Float.compare(scale, view.getScale()) == 0 && Float.compare(rotation, view.getRotation()) == 0;
        }
    }

    private View findSelection(View view) {
        if (view instanceof EntityView.SelectionView) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findSelection(group.getChildAt(i)); if (found != null) return found;
            }
        }
        return null;
    }

    /** Layout matrices map observed view-local points into source space. */
    private float[] localToSource(View view, float x, float y) {
        float[] point = {x, y};
        while (view != session) {
            if (!(view.getParent() instanceof View)) throw new AssertionError("View is not in session");
            View parent = (View) view.getParent();
            view.getMatrix().mapPoints(point);
            point[0] += view.getLeft() - parent.getScrollX();
            point[1] += view.getTop() - parent.getScrollY();
            view = parent;
        }
        return point;
    }

    private boolean sourceInside(View view, float x, float y) {
        Matrix local = new Matrix(); View current = view;
        while (current != session) {
            View parent = (View) current.getParent();
            local.postConcat(current.getMatrix());
            local.postTranslate(current.getLeft() - parent.getScrollX(), current.getTop() - parent.getScrollY());
            current = parent;
        }
        Matrix inverse = new Matrix(); if (!local.invert(inverse)) return false;
        float[] point = {x, y}; inverse.mapPoints(point);
        return point[0] >= 0 && point[0] < view.getWidth() && point[1] >= 0 && point[1] < view.getHeight();
    }

    private int observedHandle(View selection) throws Exception {
        Field field = EntityView.SelectionView.class.getDeclaredField("currentHandle");
        field.setAccessible(true); return field.getInt(selection);
    }

    private float[] handlePoint(View selection, int wanted) throws Exception {
        Method classify = selection.getClass().getDeclaredMethod("pointInsideHandle", float.class, float.class);
        classify.setAccessible(true);
        // Find a stable interior point from actual retained hit geometry, not guessed dp offsets.
        float bestScore = -1; float[] best = null;
        for (int y = 4; y < selection.getHeight() - 4; y += 2) for (int x = 4; x < selection.getWidth() - 4; x += 2) {
            if ((Integer) classify.invoke(selection, (float) x, (float) y) != wanted
                    || (Integer) classify.invoke(selection, x - 3f, y - 3f) != wanted
                    || (Integer) classify.invoke(selection, x + 3f, y + 3f) != wanted) continue;
            float[] point = localToSource(selection, x, y);
            if (point[0] < 8 || point[0] > WIDTH - 8 || point[1] < 8 || point[1] > HEIGHT - 8) continue;
            float score = -Math.abs(y - selection.getHeight() / 2f)
                    - Math.abs(x - (wanted == 1 ? selection.getWidth() / 4f : 3 * selection.getWidth() / 4f));
            if (best == null || score > bestScore) { best = point; bestScore = score; }
        }
        if (best == null) throw new AssertionError("No visible retained handle " + wanted);
        return best;
    }

    private void observe(View view, String name) {
        view.setOnTouchListener((target, event) -> {
            touched.put(name, touched.containsKey(name) ? touched.get(name) + 1 : 1);
            return false; // Observe, then let the actual existing onTouchEvent run.
        });
    }

    private int touchCount(String name) { return touched.containsKey(name) ? touched.get(name) : 0; }

    private boolean selectionReady(TextPaintView text) throws Exception {
        View selection = findSelection(session);
        Field animatorField = EntityView.class.getDeclaredField("selectAnimator");
        animatorField.setAccessible(true);
        Animator animation = (Animator) animatorField.get(text);
        // Observe completion, not float alpha equality: upstream's final alpha is
        // (1f - .8f) * 5 == .99999994f even after a healthy selection animation.
        return text.isSelected() && selection != null && selection.isAttachedToWindow() && selection.getWidth() > 0
                && !selection.isLayoutRequested() && selection.getAlpha() > 0f
                && (animation == null || (!animation.isStarted() && !animation.isRunning()));
    }

    private void selectAndObserve(TextPaintView text, boolean selected) throws Exception {
        if (selected) {
            ui(() -> { session.select(text); return null; });
            awaitState("selection animation/layout", () -> selectionReady(text));
        }
        ui(() -> {
            touched.clear(); observe(text, "body"); observe(session.retainedEntitiesView(), "container");
            View selection = findSelection(session); if (selection != null) observe(selection, "selection");
            return null;
        });
    }

    private float[] routeStart(String route, TextPaintView text) throws Exception {
        return ui(() -> {
            if (route.endsWith("handle")) return handlePoint(findSelection(session), route.startsWith("left") ? 1 : 2);
            if (!route.equals("container")) return localToSource(text, text.getWidth() / 2f, text.getHeight() / 2f);
            View selection = findSelection(session);
            for (int y = 20; y < HEIGHT - 20; y += 20) for (int x = 20; x < WIDTH - 20; x += 20)
                if (!sourceInside(text, x, y) && (selection == null || !sourceInside(selection, x, y))) return new float[]{x, y};
            throw new AssertionError("No background point available for selected container");
        });
    }

    private void entityGesture(String route, TextPaintView text, float hostScale, boolean cancel) throws Exception {
        float[] start = routeStart(route, text);
        ui(() -> {
            touched.clear();
            long down = SystemClock.uptimeMillis();
            if (!dispatch(down, 0, MotionEvent.ACTION_DOWN, start[0], start[1], 1, false))
                throw new AssertionError("Activity did not handle entity DOWN");
            if (route.endsWith("handle")) {
                check("actual selection side handler captured DOWN", observedHandle(findSelection(session)) == (route.startsWith("left") ? 1 : 2));
            }
            Geometry beforeMove = new Geometry(text);
            for (int i = 1; i <= 7; i++) {
                float fraction = i / 8f;
                float dx = route.endsWith("handle") ? (route.startsWith("left") ? -28 : 28) * fraction : 40 / hostScale * fraction;
                float dy = route.endsWith("handle") ? (route.startsWith("left") ? -32 : 32) * fraction : 0;
                dispatch(down, i, MotionEvent.ACTION_MOVE, start[0] + dx, start[1] + dy, 1, false);
            }
            check((cancel ? "CANCEL" : "commit") + " gesture changes actual entity before terminal event", !beforeMove.exact(text));
            float dx = route.endsWith("handle") ? (route.startsWith("left") ? -28 : 28) : 40 / hostScale;
            float dy = route.endsWith("handle") ? (route.startsWith("left") ? -32 : 32) : 0;
            dispatch(down, 8, cancel ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP,
                    start[0] + dx, start[1] + dy, 1, false);
            String expected = route.endsWith("handle") ? "selection" : route.equals("container") ? "container" : "body";
            check("actual " + expected + " onTouch handler received gesture", touchCount(expected) >= 8);
            if (!route.endsWith("handle")) check("body/container route did not use side handle",
                    findSelection(session) == null || observedHandle(findSelection(session)) == 0);
            return null;
        });
        idle();
    }

    private void entityRoute(String route, float hostScale) throws Exception {
        fresh(hostScale); Bitmap baseline = snap(route + "-baseline-" + hostScale);
        TextPaintView text = ui(() -> session.addText("drag", 100, 155, 22, Color.BLUE));
        laidOut(text); selectAndObserve(text, !route.equals("body"));
        Bitmap before = snap(route + "-before-" + hostScale);
        laidOut(text);
        Geometry geometry = ui(() -> new Geometry(text));
        entityGesture(route, text, hostScale, false);
        Bitmap moved = snap(route + "-moved-" + hostScale);
        check("ordinary hit-tested transform changes pixels", diff(before, moved) > 20);
        if (route.endsWith("handle")) {
            check("side handle scales actual text", ui(() -> text.getScale()) > geometry.scale * 1.05f);
            check("side handle rotates actual text", Math.abs(ui(() -> text.getRotation()) - geometry.rotation) > 10);
        } else {
            float dx = ui(() -> text.getPosition().x) - geometry.x;
            report.put(route + "SourceDxAt" + hostScale, dx);
            check("ordinary hit-tested drag moves entity", Math.abs(dx) > 20);
            if (hostScale == .5f) check("half-scale host maps 40 screen px to last MOVE source delta 70", Math.abs(dx - 70) < 3);
        }
        undo(); laidOut(text);
        check("ordinary gesture undo retains text", ui(() -> text.getParent() == session.retainedEntitiesView()));
        check("ordinary gesture undo restores exact geometry", ui(() -> geometry.exact(text)));
        check("ordinary gesture undo restores exact pixels", diff(before, snap(route + "-undo-" + hostScale)) == 0);
        // Body DOWN selects the label. Deselect and wait for removal to independently exercise body again.
        if (route.equals("body")) {
            ui(() -> { session.select(null); return null; });
            awaitState("selection removed", () -> findSelection(session) == null);
        }
        selectAndObserve(text, !route.equals("body"));
        laidOut(text);
        entityGesture(route, text, hostScale, true);
        check("gesture CANCEL retains text", ui(() -> text.getParent() == session.retainedEntitiesView()));
        check("gesture CANCEL restores exact geometry", ui(() -> geometry.exact(text)));
        check("gesture CANCEL restores exact pixels", diff(before, snap(route + "-cancel-" + hostScale)) == 0);
        undo();
        check("CANCEL adds no history: next undo removes insertion", ui(() -> text.getParent() == null));
        check("insertion undo after CANCEL restores exact baseline", diff(baseline, snap(route + "-removed-" + hostScale)) == 0);
        check("one undo per committed gesture and none for CANCEL", !ui(() -> session.canUndo()));
    }

    private void twoPointer(float scale) throws Exception {
        fresh(scale); Bitmap baseline = snap("two-pointer-baseline-" + scale);
        TextPaintView text = ui(() -> session.addText("pinch", 110, 150, 22, Color.BLUE));
        laidOut(text); selectAndObserve(text, false);
        Bitmap before = snap("two-pointer-before-" + scale);
        Geometry geometry = ui(() -> new Geometry(text));
        ui(() -> {
            float[] center = localToSource(text, text.getWidth() / 2f, text.getHeight() / 2f);
            long down = SystemClock.uptimeMillis();
            float x = center[0], y = center[1];
            dispatch(down, down, MotionEvent.ACTION_DOWN, new float[][]{{x - 12, y}}, 1, false);
            dispatch(down, down + 16, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    new float[][]{{x - 12, y}, {x + 12, y}}, 1, false);
            for (int i = 1; i <= 6; i++) dispatch(down, down + (i + 1) * 16L, MotionEvent.ACTION_MOVE,
                    new float[][]{{x - 12 - 2 * i, y - 2 * i}, {x + 12 + 2 * i, y + 2 * i}}, 1, false);
            dispatch(down, down + 128, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    new float[][]{{x - 24, y - 12}, {x + 24, y + 12}}, 1, false);
            dispatch(down, 9, MotionEvent.ACTION_UP, x - 24, y - 12, 1, false);
            return null;
        });
        idle(); Bitmap after = snap("two-pointer-after-" + scale);
        check("API29+ actual body handles two-pointer stream", ui(() -> touchCount("body")) >= 8);
        check("API29+ two-pointer scale changes", ui(() -> text.getScale()) > geometry.scale * 1.3f);
        check("API29+ two-pointer rotation changes", Math.abs(ui(() -> text.getRotation()) - geometry.rotation) > 10);
        check("API29+ two-pointer pixels change", diff(before, after) > 50);
        undo();
        check("two-pointer gesture undo retains text and exact geometry", ui(() -> text.getParent() != null && geometry.exact(text)));
        check("two-pointer gesture undo exact pixels", diff(before, snap("two-pointer-undo-" + scale)) == 0);
        undo(); check("two-pointer insertion undo exact baseline", diff(baseline, snap("two-pointer-removed-" + scale)) == 0);
    }

    private void recreation() throws Exception {
        fresh(1f);
        // Original failing source coordinates, current event sequence (not replayed original timing), including pixel (53,54).
        path(false, Color.RED, 35, 60, 270, 75, .9f, false);
        Bitmap before = snap("before-recreation");
        JSONArray cycles = new JSONArray();
        for (int i = 0; i < 3; i++) {
            RenderView renderer = ui(() -> session.retainedRenderView());
            int generation = ui(() -> session.surfaceGeneration());
            ui(() -> { session.removeView(renderer); return null; });
            awaitState("pause snapshot completed", () -> renderer.getPainting().isPaused());
            ui(() -> { session.addView(renderer, 1, new FrameLayout.LayoutParams(WIDTH, HEIGHT)); return null; });
            awaitState("new surface generation", () -> session.surfaceGeneration() > generation && session.isReady());
            idle();
            Bitmap after = snap("recreated-" + (i + 1));
            cycles.put(pixelDifference(before, after).put("cycle", i + 1)
                    .put("before53_54", String.format("%08x", before.getPixel(53, 54)))
                    .put("after53_54", String.format("%08x", after.getPixel(53, 54)))
                    .put("beforeRed53_54", Color.red(before.getPixel(53, 54)))
                    .put("afterRed53_54", Color.red(after.getPixel(53, 54))));
            check("pause resume preserves pixels " + (i + 1), diff(before, after) == 0);
            check("original red255-to254 pixel53,54 remains exact cycle " + (i + 1), before.getPixel(53, 54) == after.getPixel(53, 54));
        }
        report.put("recreationCycles", cycles);
    }

    private void exportContract() throws Exception {
        fresh(.5f);
        path(false, Color.RED, 25, 55, 295, 55, .9f, false);
        TextPaintView text = ui(() -> session.addText("PNG", 160, 150, 22, Color.BLUE)); laidOut(text);
        Bitmap image = snap("final-export"); File png = save(image, "png-roundtrip");
        Bitmap decoded = BitmapFactory.decodeFile(png.getPath());
        check("export remains source dimensions", image.getWidth() == WIDTH && image.getHeight() == HEIGHT);
        check("PNG encode decode exact", decoded != null && diff(image, decoded) == 0);
        check("caller source unchanged", diff(input, inputBefore) == 0);
        if (decoded != null) decoded.recycle();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (session != null) session.close();
        super.onDestroy();
    }
}
