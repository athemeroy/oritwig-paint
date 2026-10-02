/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
package dev.oritwig.markup.core.views;

import dev.oritwig.markup.platform.Platform;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.graphics.text.LineBreaker;
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;









import dev.oritwig.markup.core.Swatch;
import dev.oritwig.markup.support.RectOld;

public class TextPaintView extends EntityView {

    private EditTextOutline editText;
    private Swatch swatch;
    private int currentType;
    private int baseFontSize;
    private int align;

    private Typeface typeface = Typeface.DEFAULT_BOLD;

    public TextPaintView(Context context, PointF position, int fontSize, CharSequence text, Swatch swatch, int type) {
        super(context, position);

        baseFontSize = fontSize;

        editText = new EditTextOutline(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (selectionView == null || selectionView.getVisibility() != VISIBLE) {
                    return false;
                }
                return super.dispatchTouchEvent(event);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateSelectionView();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                updateSelectionView();
            }
        };

        editText.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        editText.setBackgroundColor(Color.TRANSPARENT);
        editText.setPadding(dp(7), dp(7), dp(7), dp(7));
        editText.setClickable(false);
        editText.setEnabled(false);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseFontSize);
        editText.setText(text);
        updateHint();
        editText.setTextColor(swatch.color);
        editText.setTypeface(null, Typeface.BOLD);
        editText.setHorizontallyScrolling(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        } else {
            editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        }
        editText.setFocusableInTouchMode(true);
        editText.setInputType(EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setSingleLine(false);
        addView(editText, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            editText.setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE);
        } else if (Build.VERSION.SDK_INT >= 23) {
            editText.setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE);
        }

        setSwatch(swatch);
        setType(type);

        updatePosition();

        editText.addTextChangedListener(new TextWatcher() {
            boolean pasted;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                pasted = after > 3;
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (pasted && minFontSize > 0 && maxFontSize > 0 && !disableAutoresize && editText.getLayout() != null) {
                    int newHeight = editText.getLayout().getHeight();
                    float maxHeight = getResources().getDisplayMetrics().heightPixels / 3f;
                    if (newHeight > maxHeight) {
                        float scale = maxHeight / newHeight;
                        int newFontSize = Platform.bound((int) (scale * getBaseFontSize()), maxFontSize, minFontSize);
                        if (newFontSize != getBaseFontSize()) {
                            setBaseFontSize(newFontSize);
                            if (onFontChange != null) {
                                onFontChange.run();
                            }
                        }
                    }
                }
                updateHint();
            }
        });
    }

    @Override
    protected float getStickyPaddingLeft() {
        return editText.framePadding == null ? 0 : editText.framePadding.left;
    }

    @Override
    protected float getStickyPaddingRight() {
        return editText.framePadding == null ? 0 : editText.framePadding.right;
    }

    @Override
    protected float getStickyPaddingTop() {
        return editText.framePadding == null ? 0 : editText.framePadding.top;
    }

    @Override
    protected float getStickyPaddingBottom() {
        return editText.framePadding == null ? 0 : editText.framePadding.bottom;
    }

    private void updateHint() {
        if (editText.getText().length() <= 0) {
            editText.setHint("Text");
            editText.setHintTextColor(0x60ffffff);
        } else {
            editText.setHint(null);
        }
    }

    public TextPaintView(Context context, TextPaintView textPaintView, PointF position) {
        this(context, position, textPaintView.baseFontSize, textPaintView.getText(), textPaintView.getSwatch(), textPaintView.currentType);
        setRotation(textPaintView.getRotation());
        setScale(textPaintView.getScale());
        setTypeface(textPaintView.getTypeface());
        setAlign(textPaintView.getAlign());

        int gravity;
        switch (getAlign()) {
            default:
            case 0:
                gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
                break;
            case 1:
                gravity = Gravity.CENTER;
                break;
            case 2:
                gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
                break;
        }

        editText.setGravity(gravity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            int textAlign;
            switch (getAlign()) {
                default:
                case 0:
                    textAlign = (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) ? View.TEXT_ALIGNMENT_TEXT_END : View.TEXT_ALIGNMENT_TEXT_START;
                    break;
                case 1:
                    textAlign = View.TEXT_ALIGNMENT_CENTER;
                    break;
                case 2:
                    textAlign = (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) ? View.TEXT_ALIGNMENT_TEXT_START : View.TEXT_ALIGNMENT_TEXT_END;
                    break;
            }
            editText.setTextAlignment(textAlign);
        }
    }

    public int getBaseFontSize() {
        return baseFontSize;
    }

    private int minFontSize, maxFontSize;
    private Runnable onFontChange;
    public void setMinMaxFontSize(int min, int max, Runnable onFontChange) {
        minFontSize = min;
        maxFontSize = max;
        this.onFontChange = onFontChange;
    }

    private boolean disableAutoresize;
    public void disableAutoresize(boolean disable) {
        disableAutoresize = disable;
    }

    public void setBaseFontSize(int baseFontSize) {
        this.baseFontSize = baseFontSize;

        editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseFontSize);


    }

    public void setAlign(int align) {
        this.align = align;
    }

    public int getAlign() {
        return align;
    }

    public void setTypeface(Typeface typeface) {
        this.typeface = typeface;
        if (typeface != null) {
            editText.setTypeface(typeface);
        }
        updateSelectionView();
    }

    public Typeface getTypeface() { return typeface; }

    public EditTextOutline getEditText() {
        return editText;
    }

    public void setMaxWidth(int maxWidth) {
        editText.setMaxWidth(maxWidth);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updatePosition();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updatePosition();
    }

    public CharSequence getText() {
        return editText.getText();
    }

    public void setText(CharSequence text) {
        editText.setText(text);
        updateHint();
    }

    public Paint.FontMetricsInt getFontMetricsInt() {
        return editText.getPaint().getFontMetricsInt();
    }

    public float getFontSize() {
        return editText.getTextSize();
    }

    public View getFocusedView() {
        return editText;
    }

    public void beginEditing() {
        editText.setEnabled(true);
        editText.setClickable(true);
        editText.requestFocus();
        editText.setSelection(editText.getText().length());
        Platform.runOnUIThread(() -> Platform.showKeyboard(editText), 300);
    }

    public void endEditing() {
        editText.clearFocus();
        editText.setEnabled(false);
        editText.setClickable(false);
        updateSelectionView();
    }

    public Swatch getSwatch() {
        return swatch;
    }

    public int getTextSize() {
        return (int) editText.getTextSize();
    }

    public void setSwatch(Swatch swatch) {
        this.swatch = swatch.clone();
        updateColor();
    }

    public void setType(int type) {
        if (type != 3) throw new IllegalArgumentException("Only plain text is supported");
        currentType = type;
        updateColor();
    }

    public int getType() {
        return currentType;
    }



    public void updateColor() { editText.setTextColor(swatch.color); editText.setHighlightColor(Platform.setAlphaComponent(swatch.color, 102)); }

    @Override
    public RectOld getSelectionBounds() {
        ViewGroup parentView = (ViewGroup) getParent();
        if (parentView == null) {
            return new RectOld();
        }
        float scale = parentView.getScaleX();
        float width = getMeasuredWidth() * getScale() + dp(64) / scale;
        float height = getMeasuredHeight() * getScale() + dp(52) / scale;
        float left = (getPositionX() - width / 2.0f) * scale;
        float right = left + width * scale;
        return new RectOld(left, (getPositionY() - (height - editText.getExtendedPaddingTop() - dpf2(4f)) / 2f) * scale, right - left, (height - editText.getExtendedPaddingBottom()) * scale);
    }

    protected TextViewSelectionView createSelectionView() {
        return new TextViewSelectionView(getContext());
    }

    public class TextViewSelectionView extends SelectionView {
        
        private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public TextViewSelectionView(Context context) {
            super(context);
            clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        @Override
        protected int pointInsideHandle(float x, float y) {
            float thickness = dp(1.0f);
            float radius = dp(19.5f);

            float inset = radius + thickness;
            float width = getMeasuredWidth() - inset * 2;
            float height = getMeasuredHeight() - inset * 2;

            float middle = inset + height / 2.0f;

            if (x > inset - radius && y > middle - radius && x < inset + radius && y < middle + radius) {
                return SELECTION_LEFT_HANDLE;
            } else if (x > inset + width - radius && y > middle - radius && x < inset + width + radius && y < middle + radius) {
                return SELECTION_RIGHT_HANDLE;
            }

            if (x > inset && x < width && y > inset && y < height) {
                return 0;
            }

            return 0;
        }

        private Path path = new Path();

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int count = canvas.getSaveCount();

            float alpha = getShowAlpha();
            if (alpha <= 0) {
                return;
            } else if (alpha < 1) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
            }

            float thickness = dp(2.0f);
            float radius = dpf2(5.66f);

            float inset = radius + thickness + dp(15);

            float width = getMeasuredWidth() - inset * 2;
            float height = getMeasuredHeight() - inset * 2;

            Platform.rectTmp.set(inset, inset, inset + width, inset + height);

            float R = dp(12);
            float rx = Math.min(R, width / 2f), ry = Math.min(R, height / 2f);

            path.rewind();
            Platform.rectTmp.set(inset, inset, inset + rx * 2, inset + ry * 2);
            path.arcTo(Platform.rectTmp, 180, 90);
            Platform.rectTmp.set(inset + width - rx * 2, inset, inset + width, inset + ry * 2);
            path.arcTo(Platform.rectTmp, 270, 90);
            canvas.drawPath(path, paint);

            path.rewind();
            Platform.rectTmp.set(inset, inset + height - ry * 2, inset + rx * 2, inset + height);
            path.arcTo(Platform.rectTmp, 180, -90);
            Platform.rectTmp.set(inset + width - rx * 2, inset + height - ry * 2, inset + width, inset + height);
            path.arcTo(Platform.rectTmp, 90, -90);
            canvas.drawPath(path, paint);

            canvas.drawCircle(inset, inset + height / 2.0f, radius, dotStrokePaint);
            canvas.drawCircle(inset, inset + height / 2.0f, radius - dp(1) + 1, dotPaint);

            canvas.drawCircle(inset + width, inset + height / 2.0f, radius, dotStrokePaint);
            canvas.drawCircle(inset + width, inset + height / 2.0f, radius - dp(1) + 1, dotPaint);

            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);

            canvas.drawLine(inset, inset + ry, inset, inset + height - ry, paint);
            canvas.drawLine(inset + width, inset + ry, inset + width, inset + height - ry, paint);
            canvas.drawCircle(inset + width, inset + height / 2.0f, radius + dp(1) - 1, clearPaint);
            canvas.drawCircle(inset, inset + height / 2.0f, radius + dp(1) - 1, clearPaint);

            canvas.restoreToCount(count);
        }
    }

    protected int dp(float value) { return Platform.dp(getContext(), value); }
    protected float dpf2(float value) { return Platform.dpf2(getContext(), value); }
}

