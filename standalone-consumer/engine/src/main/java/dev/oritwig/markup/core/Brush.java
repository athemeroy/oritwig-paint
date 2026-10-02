/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
package dev.oritwig.markup.core;

import dev.oritwig.markup.platform.Platform;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;



import dev.oritwig.telegram.markup.R;

import java.util.Arrays;
import java.util.List;

public abstract class Brush {
    public static List<Brush> BRUSHES_LIST = Arrays.asList(new Radial(), new Arrow());

    public static final int PAINT_TYPE_BLIT = 0;
    public static final int PAINT_TYPE_COMPOSITE = 1;
    public static final int PAINT_TYPE_BRUSH = 2;

    public float getSpacing() {
        return 0.15f;
    }

    public float getAlpha() {
        return 0.85f;
    }

    public float getOverrideAlpha() {
        return 1f;
    }

    public float getAngle() {
        return 0.0f;
    }

    public float getScale() {
        return 1.0f;
    }

    public float getPreviewScale() {
        return 0.4f;
    }

    public float getDefaultWeight() {
        return 0.25f;
    }

    public boolean isEraser() {
        return false;
    }

    public String getShaderName(int paintType) {
        switch (paintType) {
            case PAINT_TYPE_BLIT:
                return "blitWithMask";
            case PAINT_TYPE_COMPOSITE:
                return "compositeWithMask";
            case PAINT_TYPE_BRUSH:
                return "brush";
        }
        return null;
    }

    public int getStampResId() {
        return R.drawable.paint_radial_brush;
    }

    public Bitmap getStamp(android.content.res.Resources resources) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        return BitmapFactory.decodeResource(resources, getStampResId(), options);
    }

    public float getSmoothThicknessRate() {
        return 1f;
    }

    public int getIconRes() {
        return 0;
    }

    public int getDefaultColor() {
        return 0xff000000;
    }
    public static class Radial extends Brush {}
    public static class Arrow extends Brush {

        @Override
        public float getSmoothThicknessRate() {
            return .25f;
        }



        @Override
        public float getDefaultWeight() {
            return 0.25f;
        }

        @Override
        public int getDefaultColor() {
            return 0xffff9600;
        }
    }

}
