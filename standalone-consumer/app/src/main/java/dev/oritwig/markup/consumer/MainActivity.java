package dev.oritwig.markup.consumer;

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

/** Independent attachment host consuming only the public MarkupSession adapter via AnnotationEditor.
 * Its local note screenshot stands in for a caller-supplied, already-decoded attachment. */
public final class MainActivity extends Activity {
    private FrameLayout screen;
    private LinearLayout attachmentScreen, noteCard;
    private EditText note;
    private TextView resultStatus;
    private ImageView preview;
    private Button annotate;
    private Bitmap attachmentSource;
    private AnnotationEditor.PngResult result;
    private AnnotationEditor editor;
    private boolean destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        screen = new FrameLayout(this);
        screen.setFitsSystemWindows(true);
        attachmentScreen = new LinearLayout(this);
        attachmentScreen.setOrientation(LinearLayout.VERTICAL);
        attachmentScreen.setPadding(dp(18), dp(16), dp(18), dp(12));
        attachmentScreen.setBackgroundColor(0xfff8f5ee);
        screen.addView(attachmentScreen, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);
        text(attachmentScreen, "Note attachment", 26, 0xff4a3928);
        text(attachmentScreen, "Prepare an image attachment for a local message draft", 15, 0xff685740);
        noteCard = new LinearLayout(this);
        noteCard.setOrientation(LinearLayout.VERTICAL);
        noteCard.setPadding(dp(12), dp(8), dp(12), dp(8));
        noteCard.setBackgroundColor(0xfffffdf6);
        text(noteCard, "Draft note", 18, 0xff4a3928);
        note = new EditText(this);
        note.setHint("Write a note to attach as an image");
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setMinLines(2);
        note.setMaxLines(4);
        if (state != null) note.setText(state.getString("note", ""));
        noteCard.addView(note, new LinearLayout.LayoutParams(-1, -2));
        attachmentScreen.addView(noteCard, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout commands = new LinearLayout(this);
        Button attach = new Button(this);
        attach.setText("Attach note image");
        attach.setOnClickListener(v -> captureAttachment());
        commands.addView(attach, new LinearLayout.LayoutParams(0, -2, 1));
        annotate = new Button(this);
        annotate.setText("Annotate attachment");
        annotate.setEnabled(false);
        annotate.setOnClickListener(v -> annotateAttachment());
        commands.addView(annotate, new LinearLayout.LayoutParams(0, -2, 1));
        attachmentScreen.addView(commands);
        resultStatus = text(attachmentScreen, "No attachment. Create one from the note above.", 14, 0xff4a3928);
        resultStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(true);
        preview.setBackgroundColor(0xffe9e0d0);
        preview.setContentDescription("Local message attachment preview. No attachment.");
        attachmentScreen.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        text(attachmentScreen, "Local draft only. Nothing is sent. Images over 2048 pixels per side are rejected without resizing.", 12, 0xff685740);
        if (state != null) resultStatus.setText(state.getBoolean("editing")
            ? "Annotation interrupted by recreation. No PNG returned; attach the note again."
            : "Note restored. Attach the note again to restore its process-local image.");
    }

    private TextView text(LinearLayout parent, String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(4), 0, dp(6));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void captureAttachment() {
        if (editor != null || destroyed) return;
        int width = noteCard.getWidth(), height = noteCard.getHeight();
        if (width < 1 || height < 1 || width > 2048 || height > 2048) {
            resultStatus.setText("Attachment rejected: " + width + " × " + height + ". Maximum is 2048 pixels per side; nothing was resized.");
            return;
        }
        Bitmap next = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        noteCard.draw(new Canvas(next));
        preview.setImageDrawable(null);
        if (attachmentSource != null) attachmentSource.recycle();
        if (result != null) { result.close(); result = null; }
        attachmentSource = next;
        preview.setImageBitmap(next);
        preview.setContentDescription("Original note image attachment, " + width + " by " + height + " pixels");
        resultStatus.setText("Original attachment ready · " + width + " × " + height + "\nChoose Annotate attachment to mark it up");
        annotate.setEnabled(true);
    }

    private void annotateAttachment() {
        if (editor != null || attachmentSource == null || destroyed) return;
        // Re-edit the current flattened result if present; it remains immutable until a new result returns.
        Bitmap input = result == null ? attachmentSource : result.preview;
        try {
            editor = new AnnotationEditor(this, input, "Annotate note attachment", new AnnotationEditor.Listener() {
                @Override public void onFinished(AnnotationEditor.PngResult completed) {
                    editor = null;
                    attachmentScreen.setVisibility(View.VISIBLE);
                    if (destroyed) { completed.close(); return; }
                    preview.setImageDrawable(null);
                    if (result != null) result.close();
                    result = completed;
                    preview.setImageBitmap(completed.preview);
                    preview.setContentDescription("Returned flat PNG in the message attachment slot, " + completed.width + " by " + completed.height + " pixels");
                    resultStatus.setText("Annotated attachment ready · PNG " + completed.width + " × " + completed.height + " · " + completed.png.length + " bytes\nLocal message draft; not sent");
                }
                @Override public void onCancelled() {
                    editor = null;
                    attachmentScreen.setVisibility(View.VISIBLE);
                    if (!destroyed) resultStatus.setText("Annotation cancelled. Existing attachment kept unchanged.");
                }
            });
            screen.addView(editor, new FrameLayout.LayoutParams(-1, -1));
            attachmentScreen.setVisibility(View.INVISIBLE);
            editor.requestFocus();
        } catch (RuntimeException failure) {
            if (editor != null) editor.dispose();
            editor = null;
            attachmentScreen.setVisibility(View.VISIBLE);
            resultStatus.setText("Editor could not open: " + failure.getMessage() + ". Existing attachment kept.");
        }
    }

    @Override public void onBackPressed() {
        if (editor != null) editor.cancel();
        else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("note", note.getText().toString());
        out.putBoolean("editing", editor != null);
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (editor != null) { editor.dispose(); editor = null; }
        preview.setImageDrawable(null);
        if (result != null) { result.close(); result = null; }
        if (attachmentSource != null) { attachmentSource.recycle(); attachmentSource = null; }
        super.onDestroy();
    }
}
