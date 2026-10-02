package dev.oritwig.markup.platform;
import android.content.Context;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
/** Explicit-context Android adapters; no global Context, account, protocol or service singleton. */
public final class Platform {
 private static final Handler MAIN = new Handler(Looper.getMainLooper());
 public static final RectF rectTmp = new RectF(); // UI-thread-only selection drawing scratch rect
 public static int dp(Context context,float v) { return (int)Math.ceil(dpf2(context,v)); }
 public static float dpf2(Context context,float v) { return context.getResources().getDisplayMetrics().density*v; }
 public static float lerp(float a,float b,float f) { return a+f*(b-a); }
 // Telegram AndroidUtilities.java at pin: original shortest-path angle interpolation.
 public static float lerpAngle(float a,float b,float f) {float delta=((b-a+360+180)%360)-180;return (a+delta*f+360)%360;}
 public static float bound(float value,float max,float min) { return Math.max(min,Math.min(max,value)); }
 public static int bound(int value,int max,int min) { return Math.max(min,Math.min(max,value)); }
 public static int setAlphaComponent(int color,int alpha) { return (color & 0x00ffffff) | ((alpha & 255)<<24); }
 public static void runOnUIThread(Runnable r) { if(Looper.myLooper()==Looper.getMainLooper())r.run();else MAIN.post(r); }
 public static void runOnUIThread(Runnable r,long delay) { MAIN.postDelayed(r,delay); }
 public static void cancelRunOnUIThread(Runnable r) { if(r!=null)MAIN.removeCallbacks(r); }
 public static void removeFromParent(View v) { if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v); }
 public static void showKeyboard(View v) { ((InputMethodManager)v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(v,InputMethodManager.SHOW_IMPLICIT); }
 public static void log(Object error) { if(error instanceof Throwable) android.util.Log.e("OritwigMarkup","Engine error",(Throwable)error);else android.util.Log.e("OritwigMarkup",String.valueOf(error)); }
 public static void log(Object error,boolean ignored) { log(error); }
}
