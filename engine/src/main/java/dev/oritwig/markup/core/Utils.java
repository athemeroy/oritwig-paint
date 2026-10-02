/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
package dev.oritwig.markup.core;

import dev.oritwig.markup.platform.Platform;

import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

public class Utils {

    public static void HasGLError() {
        int error = GLES20.glGetError();
        if (error != 0) {
            Log.d("Paint", GLUtils.getEGLErrorString(error));
        }
    }

    public static void RectFIntegral(RectF rect) {
        rect.left = (int) Math.floor(rect.left);
        rect.top = (int) Math.floor(rect.top);
        rect.right = (int) Math.ceil(rect.right);
        rect.bottom = (int) Math.ceil(rect.bottom);
    }
}
