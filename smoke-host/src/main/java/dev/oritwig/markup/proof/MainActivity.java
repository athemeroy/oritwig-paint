package dev.oritwig.markup.proof;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Ordinary feedback host. Nothing draws or exports until the user opens the editor. */
public final class MainActivity extends Activity {
    private FrameLayout screen;
    private LinearLayout feedback;
    private EditText description;
    private TextView resultStatus;
    private ImageView preview;
    private Bitmap capture;
    private AnnotationEditor editor;
    private AnnotationEditor.PngResult result;
    private boolean destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        screen = new FrameLayout(this);
        screen.setFitsSystemWindows(true);
        feedback = new LinearLayout(this);
        feedback.setOrientation(LinearLayout.VERTICAL);
        feedback.setPadding(dp(20), dp(16), dp(20), dp(12));
        feedback.setBackgroundColor(0xfff7f9fc);
        screen.addView(feedback, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);

        text("Feedback", 26, 0xff183b66);
        text("Add a screenshot to a local feedback draft", 16, 0xff3c4b60);
        text("This form stays on this device. Nothing is submitted.", 13, 0xff536276);
        description = new EditText(this);
        description.setHint("What would you like to report?");
        description.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        description.setMinLines(3);
        description.setMaxLines(5);
        if (state != null) description.setText(state.getString("draft", ""));
        feedback.addView(description, new LinearLayout.LayoutParams(-1, -2));

        Button screenshot = new Button(this);
        screenshot.setText("Capture screen and annotate");
        screenshot.setOnClickListener(v -> beginScreenshot());
        feedback.addView(screenshot, new LinearLayout.LayoutParams(-1, -2));
        resultStatus = text("No screenshot attached", 14, 0xff183b66);
        resultStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        preview = new ImageView(this);
        preview.setBackgroundColor(0xffe3eaf2);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(true);
        preview.setContentDescription("Feedback screenshot preview. No screenshot attached.");
        feedback.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        text("Capture is limited to 2048 pixels per side. Larger screens are rejected without resizing.", 12, 0xff536276);
        if (state != null) resultStatus.setText(state.getBoolean("editing")
            ? "Annotation interrupted by recreation. No new PNG returned; capture again."
            : "Draft restored. Screenshot previews are process-local; capture again.");
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(4), 0, dp(6));
        feedback.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void beginScreenshot() {
        if (editor != null || destroyed) return;
        int width = feedback.getWidth(), height = feedback.getHeight();
        if (width < 1 || height < 1 || width > 2048 || height > 2048) {
            resultStatus.setText("Capture rejected: " + width + " × " + height + ". Use a screen at most 2048 pixels per side; no resizing was performed.");
            return;
        }
        try {
            // Actual ordinary host content, captured before the editor exists. No artwork fixture.
            capture = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            feedback.draw(new Canvas(capture));
            editor = new AnnotationEditor(this, capture, "Annotate feedback screenshot", new AnnotationEditor.Listener() {
                @Override public void onFinished(AnnotationEditor.PngResult completed) {
                    editor = null;
                    feedback.setVisibility(View.VISIBLE);
                    recycleCapture();
                    if (destroyed) { completed.close(); return; }
                    preview.setImageDrawable(null);
                    if (result != null) result.close();
                    result = completed;
                    preview.setImageBitmap(completed.preview);
                    preview.setContentDescription("Returned flat PNG attached to feedback, " + completed.width + " by " + completed.height + " pixels");
                    resultStatus.setText("Screenshot attached · PNG " + completed.width + " × " + completed.height + " · " + completed.png.length + " bytes\nLocal draft only; not submitted");
                }
                @Override public void onCancelled() {
                    editor = null;
                    feedback.setVisibility(View.VISIBLE);
                    recycleCapture();
                    if (!destroyed) resultStatus.setText(result == null
                        ? "Annotation cancelled. No screenshot attached."
                        : "Annotation cancelled. Previous screenshot attachment kept.");
                }
            });
            screen.addView(editor, new FrameLayout.LayoutParams(-1, -1));
            feedback.setVisibility(View.INVISIBLE);
            editor.requestFocus();
        } catch (RuntimeException failure) {
            if (editor != null) editor.dispose();
            editor = null;
            feedback.setVisibility(View.VISIBLE);
            recycleCapture();
            resultStatus.setText("Editor could not open: " + failure.getMessage() + ". No attachment changed.");
        }
    }

    private void recycleCapture() {
        if (capture != null) { capture.recycle(); capture = null; }
    }

    @Override public void onBackPressed() {
        if (editor != null) editor.cancel();
        else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("draft", description.getText().toString());
        out.putBoolean("editing", editor != null);
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (editor != null) { editor.dispose(); editor = null; }
        recycleCapture();
        preview.setImageDrawable(null);
        if (result != null) { result.close(); result = null; }
        super.onDestroy();
    }
}
