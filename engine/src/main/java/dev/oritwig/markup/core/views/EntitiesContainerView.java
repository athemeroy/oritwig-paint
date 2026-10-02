/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
package dev.oritwig.markup.core.views;

import dev.oritwig.markup.platform.Platform;

import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

import dev.oritwig.markup.platform.MathAdapter;


public class EntitiesContainerView extends FrameLayout {

    public boolean drawForThumb;

    public interface EntitiesContainerViewDelegate {
        boolean shouldReceiveTouches();
        void onEntityDeselect();
        EntityView onSelectedEntityRequest();
    }

    private EntitiesContainerViewDelegate delegate;
    private float previousScale = 1.0f;
    private float previousAngle;
    private boolean hasTransformed;

    public EntitiesContainerView(Context context, EntitiesContainerViewDelegate entitiesContainerViewDelegate) {
        super(context);
        delegate = entitiesContainerViewDelegate;
    }

    public int entitiesCount() {
        int count = 0;
        for (int index = 0; index < getChildCount(); index++) {
            View view = getChildAt(index);
            if (!(view instanceof EntityView)) {
                continue;
            }
            count++;
        }
        return count;
    }

    private float px, py;
    private boolean cancelled;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        EntityView selectedEntity = delegate.onSelectedEntityRequest();
        if (selectedEntity == null) {
            return false;
        }

        if (event.getPointerCount() == 1) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                hasTransformed = false;
                selectedEntity.hasPanned = false;
                selectedEntity.hasReleased = false;
                px = event.getX();
                py = event.getY();
                cancelled = false;
            } else if (!cancelled && action == MotionEvent.ACTION_MOVE) {
                final float x = event.getX();
                final float y = event.getY();
                if (hasTransformed || MathAdapter.distance(x, y, px, py) > android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                    hasTransformed = true;
                    selectedEntity.hasPanned = true;
                    selectedEntity.pan(x - px, y - py);
                    px = x;
                    py = y;
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                selectedEntity.hasPanned = false;
                selectedEntity.hasReleased = true;
                if (!hasTransformed && delegate != null) {
                    delegate.onEntityDeselect();
                }
                invalidate();
                return false;
            }
        } else {
            selectedEntity.hasPanned = false;
            selectedEntity.hasReleased = true;
            hasTransformed = false;
            cancelled = true;
            invalidate();
        }
        return true;
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
        if (child instanceof TextPaintView) {
            final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin + widthUsed, lp.width);
            child.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        } else {
            super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed);
        }
    }



    protected int dp(float value) { return Platform.dp(getContext(), value); }
    protected float dpf2(float value) { return Platform.dpf2(getContext(), value); }
}

