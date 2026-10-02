package dev.oritwig.markup.platform;
/** Platform math adapter replacing ZXing Euclidean distance and AndroidX primitive clamp calls. */
public final class MathAdapter {
 public static float distance(float a,float b,float c,float d) { return (float)Math.hypot(a-c,b-d); }
 public static float clamp(float v,float lo,float hi) { return Math.max(lo,Math.min(hi,v)); }
 public static int clamp(int v,int lo,int hi) { return Math.max(lo,Math.min(hi,v)); }
}
