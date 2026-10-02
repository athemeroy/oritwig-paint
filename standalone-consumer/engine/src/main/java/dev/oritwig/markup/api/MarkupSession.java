package dev.oritwig.markup.api;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import dev.oritwig.markup.core.*;
import dev.oritwig.markup.core.views.*;
import dev.oritwig.markup.support.Size;
/** Bounded source-pixel annotation session. Main-thread API; caller retains input ownership.
 * Flat output only. Host may apply an invertible affine transform to this view or its ancestors.
 * Finish waits for the retained arrow animation AND the GL commit, not merely the surface.
 */
public final class MarkupSession extends FrameLayout implements AutoCloseable {
 public enum Tool { PEN, STROKE_ARROW }
 public enum Failure { SURFACE_UNAVAILABLE, SESSION_CLOSED, GESTURE_IN_PROGRESS, PIXEL_READBACK_FAILED, EXPORT_IN_PROGRESS, COMMIT_TIMEOUT, RENDERER_FAILED }
 public interface FailureListener { void onFailure(Failure failure); }
 public interface IdleCallback { void onIdle(); void onFailure(Failure failure); }
 public interface ExportCallback { void onExport(Bitmap bitmap); void onFailure(Failure failure); }
 private final Bitmap source, overlay;
 private final File cache;
 private final RenderView renderer;
 private final UndoStore undo = new UndoStore();
 private final EntitiesContainerView entities;
 private final FrameLayout selections;
 private final Handler handler = new Handler(Looper.getMainLooper());
 private final List<Runnable> idleListeners = new ArrayList<>();
 private final List<IdleCallback> idleCallbacks = new ArrayList<>();
 private final Map<EntityView,EntityView.TransformSnapshot> gesture = new LinkedHashMap<>();
 private TextPaintView selected;
 private boolean closed, ready, pointerDown, finishing, rendererFailed;
 private Runnable onReady;
 private FailureListener failureListener;
 private int surfaceGeneration;
 private ExportCallback pendingExport;
 private boolean readingExport;
 private int exportGeneration;
 private final Runnable exportTimeout = () -> failExport(Failure.COMMIT_TIMEOUT);
 public MarkupSession(Context context, Bitmap input, File ownedCacheDirectory) {
  super(context); main();
  if(input==null || input.isRecycled() || input.getWidth()<1 || input.getHeight()<1 || input.getWidth()>2048 || input.getHeight()>2048)
   throw new IllegalArgumentException("Input must be a decoded image of 1–2048 pixels per side; no implicit resize");
  cache=new File(Objects.requireNonNull(ownedCacheDirectory),"markup-"+UUID.randomUUID());
  if(!cache.mkdirs())throw new IllegalStateException("Cannot create session cache");
  source=input.copy(Bitmap.Config.ARGB_8888,true); overlay=Bitmap.createBitmap(input.getWidth(),input.getHeight(),Bitmap.Config.ARGB_8888);
  ImageView image=new ImageView(context); image.setImageBitmap(source); image.setScaleType(ImageView.ScaleType.FIT_XY); addView(image,params());
  Painting painting=new Painting(new Size(input.getWidth(),input.getHeight()),cache,context.getResources());
  renderer=new RenderView(context,painting,overlay); renderer.setUndoStore(undo);
  renderer.setDelegate(new RenderView.RenderViewDelegate(){
   public void onBeganDrawing(){} public void onFinishedDrawing(boolean moved){}
   public boolean shouldDraw(){return !closed && !finishing;}
   public void resetBrush(){if(!closed && !finishing)setTool(Tool.PEN);}
   public void onFirstDraw(){if(closed||rendererFailed)return;ready=true;surfaceGeneration++;if(onReady!=null)onReady.run();notifyIdle();}
   public void onInputIdle(){notifyIdle();}
   public void onSurfaceUnavailable(){ready=false;failIdle(Failure.SURFACE_UNAVAILABLE);failExport(Failure.SURFACE_UNAVAILABLE);}
   public void onRenderFailure(){rendererFailed=true;ready=false;failIdle(Failure.RENDERER_FAILED);failExport(Failure.RENDERER_FAILED);if(failureListener!=null)failureListener.onFailure(Failure.RENDERER_FAILED);}
  });
  renderer.setOnTouchListener((v,event)->renderer.onTouch(event)); addView(renderer,params());
  entities=new EntitiesContainerView(context,new EntitiesContainerView.EntitiesContainerViewDelegate(){
   public boolean shouldReceiveTouches(){return selected!=null;}
   public void onEntityDeselect(){select(null);}
   public EntityView onSelectedEntityRequest(){return selected;}
  }); addView(entities,params());
  selections=new FrameLayout(context); addView(selections,params());
  setTool(Tool.PEN); setColor(Color.RED); setWeight(.35f);
 }
 private LayoutParams params(){return new LayoutParams(source.getWidth(),source.getHeight());}
 private static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Main thread required");}
 private void open(){main();if(closed)throw new IllegalStateException("Session is closed");}
 private void editable(){open();if(finishing||pointerDown||!renderer.isInputIdle())throw new IllegalStateException("Wait for drawing commit");if(entities!=null)settleEntities();}
 private void settleEntities(){for(int i=0;i<entities.getChildCount();i++)((EntityView)entities.getChildAt(i)).settleTransformAnimations();}
 public int imageWidth(){return source.getWidth();} public int imageHeight(){return source.getHeight();}
 public boolean isReady(){return ready&&!closed&&renderer.isSurfaceReady();}
 public boolean isIdle(){return isReady()&&!pointerDown&&renderer.isInputIdle();}
 public int surfaceGeneration(){return surfaceGeneration;}
 public void setFailureListener(FailureListener listener){open();failureListener=listener;if(rendererFailed&&listener!=null)listener.onFailure(Failure.RENDERER_FAILED);}
 public void whenReady(Runnable listener){open();onReady=listener;if(isReady())listener.run();}
 /** One-shot callback; cancelled by close. An unavailable surface is not idle. */
 public void whenIdle(Runnable listener){open();if(isIdle())listener.run();else idleListeners.add(Objects.requireNonNull(listener));}
 public void whenIdle(IdleCallback listener){
  main();Objects.requireNonNull(listener);
  if(closed){listener.onFailure(Failure.SESSION_CLOSED);return;}
  if(rendererFailed){listener.onFailure(Failure.RENDERER_FAILED);return;}
  if(!isReady()){listener.onFailure(Failure.SURFACE_UNAVAILABLE);return;}
  if(isIdle())listener.onIdle();else idleCallbacks.add(listener);
 }
 private void failIdle(Failure failure){
  idleListeners.clear();List<IdleCallback> callbacks=new ArrayList<>(idleCallbacks);idleCallbacks.clear();for(IdleCallback cb:callbacks)cb.onFailure(failure);
 }
 private void notifyIdle(){
  if(!isIdle())return;
  if(pendingExport!=null){completeExport();return;}
  List<Runnable> callbacks=new ArrayList<>(idleListeners);idleListeners.clear();for(Runnable r:callbacks){if(!closed)r.run();}
  List<IdleCallback> explicit=new ArrayList<>(idleCallbacks);idleCallbacks.clear();for(IdleCallback cb:explicit){if(!closed)cb.onIdle();else cb.onFailure(Failure.SESSION_CLOSED);}
 }
 public void setTool(Tool tool){editable();renderer.setBrush(Objects.requireNonNull(tool)==Tool.PEN?new Brush.Radial():new Brush.Arrow());}
 public void selectDrawingTool(Tool tool){setTool(tool);select(null);}
 public void setWeight(float size){editable();if(!Float.isFinite(size)||size<0||size>1)throw new IllegalArgumentException("weight 0..1");renderer.setBrushSize(size);}
 public void setColor(int color){editable();renderer.setColor(color);}
 public boolean dispatchStroke(MotionEvent event){open();if(!isReady()||finishing)return false;select(null);return renderer.onTouch(event);}
 /** Snapshot BEFORE dispatch, covering body, selection handles, and container routes equally. */
 @Override public boolean dispatchTouchEvent(MotionEvent event){
  if(closed||finishing)return false;
  int action=event.getActionMasked();
  if(action==MotionEvent.ACTION_DOWN){
   if(!isReady()||!renderer.isInputIdle())return false;
   settleEntities();pointerDown=true;gesture.clear();
   for(int i=0;i<entities.getChildCount();i++){EntityView e=(EntityView)entities.getChildAt(i);gesture.put(e,e.captureTransform());}
  }
  boolean handled=super.dispatchTouchEvent(event);
  if(action==MotionEvent.ACTION_DOWN&&!handled){gesture.clear();pointerDown=false;}
  if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
   List<EntityView.TransformSnapshot> changed=new ArrayList<>();
   for(Map.Entry<EntityView,EntityView.TransformSnapshot> entry:gesture.entrySet())if(entry.getKey().getParent()==entities&&entry.getValue().differs())changed.add(entry.getValue());
   if(action==MotionEvent.ACTION_CANCEL){for(EntityView.TransformSnapshot s:changed)s.restore();}
   else if(!changed.isEmpty())undo.registerUndo(UUID.randomUUID(),()->{for(EntityView.TransformSnapshot s:changed)s.restore();});
   gesture.clear();pointerDown=false;notifyIdle();
  }
  return handled;
 }
 /** Maps through every host ancestor, including pivot scale, rotation, offsets and scroll. */
 private Matrix sourceToScreen(){
  Matrix m=new Matrix();View v=this;
  while(v.getParent() instanceof View){View p=(View)v.getParent();m.postConcat(v.getMatrix());m.postTranslate(v.getLeft()-p.getScrollX(),v.getTop()-p.getScrollY());v=p;}
  int[] origin=new int[2];v.getLocationOnScreen(origin);m.postTranslate(origin[0],origin[1]);return m;
 }
 public void screenToSource(float x,float y,float[] out){
  open();if(out==null||out.length<2)throw new IllegalArgumentException("Two-coordinate output required");
  Matrix inverse=new Matrix();if(!sourceToScreen().invert(inverse))throw new IllegalStateException("Host transform is not invertible");out[0]=x;out[1]=y;inverse.mapPoints(out,0,out,0,1);
 }
 public void sourceToScreen(float x,float y,float[] out){open();if(out==null||out.length<2)throw new IllegalArgumentException("Two-coordinate output required");out[0]=x;out[1]=y;sourceToScreen().mapPoints(out,0,out,0,1);}
 public TextPaintView addText(String text,float x,float y,int fontSize,int color){
  editable();validateText(text);if(!Float.isFinite(x)||!Float.isFinite(y)||fontSize<8||fontSize>256)throw new IllegalArgumentException("Invalid label geometry");
  TextPaintView t=new TextPaintView(getContext(),new PointF(x,y),fontSize,text,new Swatch(color,1,.35f),3);
  t.setMaxWidth(source.getWidth());t.setDelegate(new EntityView.EntityViewDelegate(){
   public boolean onEntitySelected(EntityView e){select((TextPaintView)e);return true;}
   public boolean onEntityLongClicked(EntityView e){return false;}
   public boolean allowInteraction(EntityView e){return !closed&&!finishing;}
   public int[] getCenterLocation(EntityView e){float[] p={e.getWidth()/2f,e.getHeight()/2f};e.getMatrix().mapPoints(p);return new int[]{Math.round(e.getLeft()+p[0]),Math.round(e.getTop()+p[1])};}
   public void getTransformedTouch(float x,float y,float[] out){screenToSource(x,y,out);}
   public float getCropRotation(){return 0;}
   public boolean isEntityDeletable(){return false;}
  });
  entities.addView(t,new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
  undo.registerUndo(t.getUUID(),()->{if(selected==t)select(null);t.cancelTransformAnimations();entities.removeView(t);});return t;
 }
 private static void validateText(String text){if(text==null||text.length()>4096)throw new IllegalArgumentException("Label limit: 4096 characters");}
 public void addLabel(String text,float x,float y,int fontSize,int color){select(addText(text,x,y,fontSize,color));}
 public String selectedTextValue(){open();return selected==null?null:selected.getText().toString();}
 public boolean hasSelectedText(){open();return selected!=null;}
 public void editSelectedText(String text){if(selected==null)throw new IllegalStateException("Select a label first");editText(selected,text);}
 public void editText(TextPaintView text,String value){editable();validateText(value);if(text.getParent()!=entities)throw new IllegalArgumentException("Label not in session");String before=text.getText().toString();if(before.equals(value))return;text.setText(value);undo.registerUndo(UUID.randomUUID(),()->text.setText(before));}
 public void select(TextPaintView text){open();if(text!=null&&text.getParent()!=entities)throw new IllegalArgumentException("Label not in session");if(selected==text)return;if(selected!=null)selected.deselect();selected=text;if(text!=null)text.select(selections);}
 public void transformSelectedText(float dx,float dy,float scale,float angleDelta){if(selected==null)throw new IllegalStateException("Select a label first");transformText(selected,dx,dy,scale,selected.getRotation()+angleDelta);}
 public void transformText(TextPaintView text,float dx,float dy,float scale,float angle){
  editable();if(text.getParent()!=entities||!Float.isFinite(dx)||!Float.isFinite(dy)||!Float.isFinite(scale)||scale<=0||scale>8||!Float.isFinite(angle))throw new IllegalArgumentException("Invalid text transform");
  EntityView.TransformSnapshot before=text.captureTransform();text.pan(dx,dy);text.scale(scale);text.rotate(angle);undo.registerUndo(UUID.randomUUID(),before::restore);
 }
 public boolean canUndo(){open();return !finishing&&!pointerDown&&renderer.isInputIdle()&&undo.canUndo();}
 public void undo(){editable();if(pointerDown)throw new IllegalStateException("Gesture in progress");undo.undo();}
 /** Synchronous compatibility API rejects a busy stroke rather than exporting partial arrows. */
 public Bitmap exportBitmap(){open();if(!isIdle())throw new IllegalStateException("Surface unavailable or gesture/commit in progress");return flatten();}
 private Bitmap flatten(){
  settleEntities();
  Bitmap annotated=renderer.getResultBitmap();if(annotated==null)throw new IllegalStateException("Pixel readback failed");
  return compose(annotated);
 }
 private Bitmap compose(Bitmap annotated){
  try{EntityComposition.draw(annotated,entities);Bitmap flattened=source.copy(Bitmap.Config.ARGB_8888,true);new Canvas(flattened).drawBitmap(annotated,0,0,null);return flattened;}finally{annotated.recycle();}
 }
 /** Locks new edits, waits for exact GL commit completion, then returns one caller-owned bitmap.
  * Callback is exactly once, on main; host PNG I/O and final close are caller responsibilities. */
 public void finish(ExportCallback callback){
  main();Objects.requireNonNull(callback);
  if(closed){callback.onFailure(Failure.SESSION_CLOSED);return;}
  if(finishing){callback.onFailure(Failure.EXPORT_IN_PROGRESS);return;}
  if(rendererFailed){callback.onFailure(Failure.RENDERER_FAILED);return;}
  if(!isReady()){callback.onFailure(Failure.SURFACE_UNAVAILABLE);return;}
  if(pointerDown){callback.onFailure(Failure.GESTURE_IN_PROGRESS);return;}
  finishing=true;pendingExport=callback;handler.postDelayed(exportTimeout,10000);if(isIdle())completeExport();
 }
 private void completeExport(){
  if(pendingExport==null||readingExport)return;
  settleEntities();readingExport=true;
  final int generation=++exportGeneration;
  renderer.getResultBitmapAsync(annotated->{
   if(generation!=exportGeneration||pendingExport==null||closed){if(annotated!=null)annotated.recycle();return;}
   if(annotated==null){failExport(Failure.PIXEL_READBACK_FAILED);return;}
   Bitmap result;try{result=compose(annotated);}catch(RuntimeException failure){failExport(Failure.PIXEL_READBACK_FAILED);return;}
   ExportCallback cb=pendingExport;pendingExport=null;finishing=false;readingExport=false;handler.removeCallbacks(exportTimeout);try{cb.onExport(result);}finally{notifyIdle();}
  });
 }
 private void failExport(Failure failure){
  exportGeneration++;readingExport=false;
  ExportCallback cb=pendingExport;pendingExport=null;finishing=false;handler.removeCallbacks(exportTimeout);if(cb!=null)cb.onFailure(failure);
 }
 public void writePng(OutputStream output)throws IOException{Bitmap b=exportBitmap();try{if(!b.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException("PNG encode failed");}finally{b.recycle();}}
 public RenderView retainedRenderView(){open();return renderer;}
 public EntitiesContainerView retainedEntitiesView(){open();return entities;}
 public void close(){
  main();if(closed)return;select(null);closed=true;ready=false;pointerDown=false;gesture.clear();failIdle(Failure.SESSION_CLOSED);
  failExport(Failure.SESSION_CLOSED);
  for(int i=0;i<entities.getChildCount();i++)((EntityView)entities.getChildAt(i)).cancelTransformAnimations();
  entities.removeAllViews();
  renderer.shutdown(()->{undo.reset();removeAllViews();File[] files=cache.listFiles();if(files!=null)for(File f:files)f.delete();cache.delete();source.recycle();overlay.recycle();});
 }
}
