package dev.oritwig.markup.proof;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import dev.oritwig.markup.api.MarkupSession;

/** Platform-only public-API client. Its callback returns a real PNG, not a renderer screenshot.
 * The caller retains ownership of input. Result ownership transfers only on onFinished.
 * No activity intent, provider, file grants or process-global bitmap registry is needed. */
final class AnnotationEditor extends LinearLayout {
    interface Listener {
        void onFinished(PngResult result);
        void onCancelled();
    }

    static final class PngResult implements AutoCloseable {
        final byte[] png;
        final Bitmap preview;
        final int width, height;
        PngResult(byte[] png, Bitmap preview) {
            this.png = png;
            this.preview = preview;
            width = preview.getWidth();
            height = preview.getHeight();
        }
        @Override public void close() {
            if (!preview.isRecycled()) preview.recycle();
        }
    }

    private final Activity activity;
    private final Listener listener;
    private final MarkupSession session;
    private final TextView status;
    private final FrameLayout viewport;
    private final View interactionShield;
    private final ArrayList<Button> editingButtons = new ArrayList<>();
    private final Button finish;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService encoder = Executors.newSingleThreadExecutor();
    private AlertDialog textDialog;
    private boolean disposed, finishing, encoding;

    AnnotationEditor(Activity activity, Bitmap input, String title, Listener listener) {
        super(activity);
        this.activity = activity;
        this.listener = listener;
        setOrientation(VERTICAL);
        setClickable(true); // Consume empty editor space instead of activating the covered host.
        setFocusableInTouchMode(true);
        View oldFocus = activity.getCurrentFocus();
        if (oldFocus != null) oldFocus.clearFocus();
        hideKeyboard(activity.getWindow().getDecorView().getWindowToken());
        setPadding(dp(12), dp(8), dp(12), dp(8));
        setBackgroundColor(0xfff2f4f7);
        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextSize(20);
        heading.setTextColor(0xff172c44);
        addView(heading);
        status = new TextView(activity);
        status.setText("Preparing image…");
        status.setTextSize(13);
        status.setMinLines(2);
        addView(status);

        LinearLayout drawing = toolRow();
        tool(drawing, "Pen", () -> chooseTool(MarkupSession.Tool.PEN));
        tool(drawing, "Arrow", () -> chooseTool(MarkupSession.Tool.STROKE_ARROW));
        tool(drawing, "Add text", () -> showTextDialog(false));
        tool(drawing, "Edit text", () -> showTextDialog(true));
        tool(drawing, "Undo", this::undo);
        LinearLayout transforms = toolRow();
        tool(transforms, "Smaller", () -> transform(.85f, 0));
        tool(transforms, "Larger", () -> transform(1.15f, 0));
        tool(transforms, "Rotate left", () -> transform(1, -15));
        tool(transforms, "Rotate right", () -> transform(1, 15));

        viewport = new FrameLayout(activity);
        viewport.setBackgroundColor(0xffcad2dd);
        viewport.setClipChildren(true);
        addView(viewport, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        session = new MarkupSession(activity, input, activity.getCacheDir());
        session.setPivotX(0);
        session.setPivotY(0);
        session.setContentDescription("Annotation image. Draw with pen or arrow. Tap a label to select and drag it.");
        viewport.addView(session, new FrameLayout.LayoutParams(input.getWidth(), input.getHeight(), Gravity.TOP | Gravity.LEFT));
        viewport.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> fitImage());
        interactionShield = new View(activity);
        interactionShield.setClickable(true);
        interactionShield.setFocusable(false);
        interactionShield.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        viewport.addView(interactionShield, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout actions = new LinearLayout(activity);
        Button cancel = new Button(activity);
        cancel.setText("Cancel");
        cancel.setContentDescription("Cancel annotation and return without a new image");
        cancel.setOnClickListener(v -> cancel());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        finish = new Button(activity);
        finish.setText("Finish");
        finish.setContentDescription("Finish annotation and return a flat PNG");
        finish.setOnClickListener(v -> finishImage());
        actions.addView(finish, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        addView(actions);
        refreshControls();
        session.whenReady(() -> {
            if (disposed) return;
            if (!finishing) status.setText("Image ready · Drag a selected label; use size / rotate buttons");
            refreshControls();
        });
        session.setFailureListener(failure -> {
            if (disposed || encoding) return; // The returned bitmap no longer depends on GL once encoding starts.
            finishing = false;
            status.setText("Renderer unavailable. Cancel and reopen the editor. No new image returned.");
            refreshControls();
        });
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private LinearLayout toolRow() {
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(activity);
        scroll.addView(row);
        addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void tool(LinearLayout row, String text, Runnable action) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(12);
        button.setMinWidth(dp(70));
        button.setOnClickListener(v -> afterIdle(action));
        row.addView(button);
        editingButtons.add(button);
    }

    private void fitImage() {
        if (disposed || viewport.getWidth() == 0 || viewport.getHeight() == 0) return;
        float scale = Math.min(viewport.getWidth() / (float) session.imageWidth(), viewport.getHeight() / (float) session.imageHeight());
        // Only the view is transformed. Source pixels and engine coordinate dimensions never change.
        session.setScaleX(scale);
        session.setScaleY(scale);
        session.setTranslationX((viewport.getWidth() - session.imageWidth() * scale) / 2f);
        session.setTranslationY((viewport.getHeight() - session.imageHeight() * scale) / 2f);
    }

    private void refreshControls() {
        if (disposed) return;
        boolean enabled = session.isReady() && !finishing;
        for (Button button : editingButtons) button.setEnabled(enabled);
        finish.setEnabled(enabled);
        interactionShield.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    private void afterIdle(Runnable action) {
        if (disposed || finishing) return;
        if (!session.isReady()) {
            status.setText("Drawing surface unavailable. Cancel and reopen the editor.");
            refreshControls();
            return;
        }
        // Never leave a host command waiting on a renderer that may lose its surface.
        // A pending arrow must commit before a tool/undo/text change; Finish has its own barrier.
        if (!session.isIdle()) {
            status.setText("Previous gesture is still finishing. Try that control again in a moment.");
            return;
        }
        try { action.run(); }
        catch (RuntimeException failure) { status.setText("Action unavailable: " + failure.getMessage()); }
        refreshControls();
    }

    private void hideKeyboard(IBinder token) {
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (keyboard != null && token != null) keyboard.hideSoftInputFromWindow(token, 0);
    }

    private void chooseTool(MarkupSession.Tool tool) {
        session.selectDrawingTool(tool);
        status.setText(tool == MarkupSession.Tool.PEN ? "Pen selected · Draw on the image" : "Arrow selected · Draw toward the arrow tip");
    }

    private void undo() {
        if (session.canUndo()) {
            session.undo();
            status.setText("Undid the last change");
        } else status.setText("Nothing to undo");
    }

    private void transform(float scale, float angleDelta) {
        if (session.selectedTextValue() == null) {
            status.setText("Tap a text label first, or add one");
            return;
        }
        session.transformSelectedText(0, 0, scale, angleDelta);
        status.setText("Selected label transformed · Undo is available");
    }

    private void showTextDialog(boolean editing) {
        String current = session.selectedTextValue();
        if (editing && current == null) {
            status.setText("Tap a text label first, then Edit text");
            return;
        }
        EditText entry = new EditText(activity);
        entry.setHint("Enter a label");
        entry.setText(editing ? current : "");
        entry.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        entry.setMinLines(2);
        entry.setMaxLines(6);
        entry.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4096)});
        entry.setSelectAllOnFocus(editing);
        entry.setPadding(dp(20), dp(12), dp(20), dp(12));
        textDialog = new AlertDialog.Builder(activity)
            .setTitle(editing ? "Edit selected label" : "Add text label")
            .setView(entry)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(editing ? "Apply" : "Add", null)
            .create();
        final IBinder[] entryWindow = new IBinder[1];
        textDialog.setOnDismissListener(ignored -> {
            hideKeyboard(entryWindow[0]);
            if (!disposed) requestFocus();
        });
        textDialog.setOnShowListener(ignored -> {
            entryWindow[0] = entry.getWindowToken();
            textDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = entry.getText().toString();
                if (value.trim().isEmpty()) { entry.setError("Enter some text"); return; }
                if (disposed) return;
                if (!session.isReady()) { entry.setError("Renderer unavailable. Cancel and reopen the editor."); return; }
                try {
                    if (editing) session.editSelectedText(value);
                    else session.addLabel(value, session.imageWidth() / 2f, session.imageHeight() / 2f,
                        Math.max(16, Math.min(96, session.imageWidth() / 16)), 0xffbd1632);
                    status.setText("Text selected · Drag it, or use size / rotate controls");
                    textDialog.dismiss();
                } catch (RuntimeException failure) { entry.setError("Could not apply label: " + failure.getMessage()); }
            });
            entry.requestFocus();
            if (textDialog.getWindow() != null) textDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        textDialog.show();
    }

    private void finishImage() {
        if (disposed || finishing) return;
        if (!session.isReady()) {
            status.setText("No image returned: drawing surface unavailable. Cancel and reopen the editor.");
            refreshControls();
            return;
        }
        finishing = true;
        status.setText("Finishing PNG…");
        refreshControls();
        session.finish(new MarkupSession.ExportCallback() {
            @Override public void onExport(Bitmap flattened) {
                if (disposed) { flattened.recycle(); return; }
                encoding = true;
                encoder.execute(() -> encode(flattened));
            }
            @Override public void onFailure(MarkupSession.Failure failure) {
                if (disposed) return;
                finishing = false;
                boolean reopen = failure == MarkupSession.Failure.RENDERER_FAILED || failure == MarkupSession.Failure.SESSION_CLOSED;
                status.setText("No image returned: " + failure.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                    + (reopen ? ". Cancel and reopen the editor." : ". Try again or Cancel."));
                refreshControls();
            }
        });
    }

    /** Called only by the encoder executor; contains no View or main-thread work. */
    private static final class PngEncoder {
        private static PngResult encode(Bitmap flattened) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!flattened.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IllegalStateException("PNG encoding failed");
            byte[] bytes = output.toByteArray();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth != flattened.getWidth() || bounds.outHeight != flattened.getHeight() || bounds.outWidth > 2048 || bounds.outHeight > 2048)
                throw new IllegalStateException("PNG dimensions changed");
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap reopened = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (reopened == null) throw new IllegalStateException("PNG could not be reopened");
            if (!reopened.sameAs(flattened)) {
                reopened.recycle();
                throw new IllegalStateException("PNG pixels changed during encoding");
            }
            return new PngResult(bytes, reopened);
        }
    }

    private void encode(Bitmap flattened) {
        PngResult result = null;
        RuntimeException failure = null;
        try {
            result = PngEncoder.encode(flattened);
        } catch (RuntimeException problem) { failure = problem; }
        finally { flattened.recycle(); }
        final PngResult completed = result;
        final RuntimeException problem = failure;
        main.post(() -> {
            if (disposed) { if (completed != null) completed.close(); return; }
            if (completed == null) {
                encoding = false;
                finishing = false;
                status.setText("No image returned: " + problem.getMessage() + ". Try again or Cancel.");
                refreshControls();
                return;
            }
            dispose();
            listener.onFinished(completed);
        });
    }

    void cancel() {
        if (disposed) return;
        dispose();
        listener.onCancelled();
    }

    /** Destroy is silent; pending exports/encodes cannot call a dead activity. */
    void dispose() {
        if (disposed) return;
        disposed = true;
        if (textDialog != null) textDialog.dismiss();
        session.close();
        encoder.shutdown();
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }
}
