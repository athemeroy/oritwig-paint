package android.graphics;
/** Test-only Android value-class substitute; never packaged. */
public class RectF {public float left,top,right,bottom;public RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}public RectF(RectF r){this(r.left,r.top,r.right,r.bottom);}public float width(){return right-left;}public float height(){return bottom-top;}}
