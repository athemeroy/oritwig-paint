/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
package dev.oritwig.markup.core.views;

import dev.oritwig.markup.platform.Platform;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.text.InputType;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;


import android.widget.EditText;

public class EditTextOutline extends EditText {

    private Canvas mCanvas = new Canvas();
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap mCache;
    private boolean mUpdateCachedBitmap;
    private int mStrokeColor;
    private float mStrokeWidth;
    private int mFrameColor;


    private RectF[] lines;
    public RectF framePadding;
    private boolean isFrameDirty;

    public EditTextOutline(Context context) {
        super(context);

        mStrokeColor = Color.TRANSPARENT;
        setInputType(getInputType() | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        mUpdateCachedBitmap = true;
        isFrameDirty = true;
        setFrameRoundRadius(dp(16));
        textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    private float lastFrameRoundRadius;
    private void setFrameRoundRadius(float roundRadius) {
        if (Math.abs(lastFrameRoundRadius - roundRadius) > 0.1f) {
            paint.setPathEffect(new CornerPathEffect(lastFrameRoundRadius = roundRadius));
        }
    }

    protected void onTextChanged(CharSequence text, int start, int before, int after) {
        super.onTextChanged(text, start, before, after);
        mUpdateCachedBitmap = true;
        isFrameDirty = true;
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            mUpdateCachedBitmap = true;
            isFrameDirty = true;
            if (mCache != null) {
                mCache.recycle();
            }
            mCache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } else {
            mCache = null;
        }
    }

    @Override
    public void setGravity(int gravity) {
        super.setGravity(gravity);
        mUpdateCachedBitmap = true;
        isFrameDirty = true;
        invalidate();
    }

    public void setStrokeColor(int strokeColor) {
        mStrokeColor = strokeColor;
        mUpdateCachedBitmap = true;
        invalidate();
    }



    public void setStrokeWidth(float strokeWidth) {
        mStrokeWidth = strokeWidth;
        mUpdateCachedBitmap = true;
        invalidate();
    }

    @SuppressLint("DrawAllocation")
    protected void onDraw(Canvas canvas) {
        if (mCache != null && mStrokeColor != Color.TRANSPARENT) {
            if (mUpdateCachedBitmap) {
                final int w = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
                final int h = getMeasuredHeight();
                final CharSequence text = getText();

                mCanvas.setBitmap(mCache);
                mCanvas.drawColor(0, PorterDuff.Mode.CLEAR);

                float strokeWidth = mStrokeWidth > 0 ? mStrokeWidth : (float) Math.ceil(getTextSize() / 11.5f);
                textPaint.setStrokeWidth(strokeWidth);
                textPaint.setColor(mStrokeColor);
                textPaint.setTextSize(getTextSize());
                textPaint.setTypeface(getTypeface());
                textPaint.setStyle(Paint.Style.FILL_AND_STROKE);

                Layout.Alignment alignment = Layout.Alignment.ALIGN_NORMAL;
                if (getLayout() != null) {
                    alignment = getLayout().getAlignment();
                }
                StaticLayout sl = new StaticLayout(text, textPaint, w, alignment, 1, 0, true);

                mCanvas.save();
                float ty = (h - getPaddingTop() - getPaddingBottom() - sl.getHeight()) / 2.0f;
                mCanvas.translate(getPaddingLeft(), ty + getPaddingTop());
                sl.draw(mCanvas);
                mCanvas.restore();

                mUpdateCachedBitmap = false;
            }
            canvas.drawBitmap(mCache, 0, 0, textPaint);
        }

        super.onDraw(canvas);
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        return super.onTextContextMenuItem(id);
    }

    protected int dp(float value) { return Platform.dp(getContext(), value); }
    protected float dpf2(float value) { return Platform.dpf2(getContext(), value); }
}
