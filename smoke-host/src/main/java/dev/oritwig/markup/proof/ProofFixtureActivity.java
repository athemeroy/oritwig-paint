package dev.oritwig.markup.proof;
import android.app.Activity;
import android.os.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.concurrent.*;
import dev.oritwig.markup.api.MarkupSession;
import dev.oritwig.markup.core.RenderView;
import dev.oritwig.markup.core.views.TextPaintView;
/** An ordinary deterministic synthetic fixture, never a product shell or device-wide fuzz runner. */
public final class ProofFixtureActivity extends Activity {
 MarkupSession session; LinearLayout root;TextView status;Bitmap input;File out;JSONObject report=new JSONObject();JSONArray checks=new JSONArray();
 interface Task<T>{T call()throws Exception;}
 <T>T ui(Task<T> task)throws Exception{FutureTask<T> f=new FutureTask<>(()->task.call());runOnUiThread(f);return f.get(60,TimeUnit.SECONDS);}
 void check(String name,boolean value)throws Exception{JSONObject c=new JSONObject().put("name",name).put("pass",value);checks.put(c);if(!value)throw new AssertionError(name);}
 void ready()throws Exception{long end=SystemClock.uptimeMillis()+60000;while(!ui(()->session.isReady())){if(SystemClock.uptimeMillis()>end)throw new AssertionError("Surface did not become ready");Thread.sleep(100);}Thread.sleep(300);}
 @Override public void onCreate(Bundle state){super.onCreate(state);
  String run=getIntent().getStringExtra("run_id");if(run==null)run="manual-"+System.currentTimeMillis();if(!run.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Bad run id");
  out=new File(getExternalFilesDir(null),"proof/"+run);out.mkdirs();
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(8,8,8,8);root.setBackgroundColor(0xffe8ebef);setContentView(root);
  status=new TextView(this);status.setText("Retained Telegram Paint core • local fixture running");root.addView(status);
  input=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);input.eraseColor(Color.WHITE);Canvas c=new Canvas(input);Paint p=new Paint();p.setColor(0xff20aa40);c.drawRect(0,0,20,20,p);p.setColor(0xffe5af00);c.drawRect(300,220,320,240,p);
  session=new MarkupSession(this,input,getCacheDir());root.addView(session,new LinearLayout.LayoutParams(320,240));
  new Thread(()->{try{runProof();report.put("status","pass");ui(()->{status.setText("PASS • Pen, stroke-arrow, text, undo, PNG and lifecycle");return null;});}catch(Throwable e){try{report.put("status","fail").put("error",e.toString());android.util.Log.e("MarkupProof","fixture failed",e);ui(()->{status.setText("FAIL • "+e);return null;});}catch(Exception ignored){}}finally{try{report.put("checks",checks).put("sdk",Build.VERSION.SDK_INT).put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL);try(FileWriter w=new FileWriter(new File(out,"result.json"))){w.write(report.toString(2));}android.util.Log.i("MarkupProof",report.toString());}catch(Exception e){android.util.Log.e("MarkupProof","report failed",e);}}},"MarkupFixture").start();
 }
 Bitmap snap(String name)throws Exception{Bitmap b=ui(()->session.exportBitmap());try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}return b;}
 int diff(Bitmap a,Bitmap b){int n=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getPixel(x,y)!=b.getPixel(x,y))n++;return n;}
 int changed(Bitmap a,Bitmap b,int x0,int y0,int x1,int y1){int n=0;for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)if(a.getPixel(x,y)!=b.getPixel(x,y))n++;return n;}
 void stroke(int tool,int color,float x0,float y0,float x1,float y1,float pressure,boolean cancel)throws Exception{
  ui(()->{session.setTool(tool==0?MarkupSession.Tool.PEN:MarkupSession.Tool.STROKE_ARROW);session.setWeight(.45f);session.setColor(color);return null;});long start=SystemClock.uptimeMillis();
  for(int i=0;i<=16;i++){final int at=i;ui(()->{MotionEvent.PointerProperties prop=new MotionEvent.PointerProperties();prop.id=0;prop.toolType=MotionEvent.TOOL_TYPE_STYLUS;MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();coords.x=x0+(x1-x0)*at/16f;coords.y=y0+(y1-y0)*at/16f;coords.pressure=pressure;coords.size=1;MotionEvent e=MotionEvent.obtain(start,start+at*16,at==0?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_MOVE,1,new MotionEvent.PointerProperties[]{prop},new MotionEvent.PointerCoords[]{coords},0,0,1,1,0,0,android.view.InputDevice.SOURCE_STYLUS,0);session.dispatchStroke(e);e.recycle();return null;});Thread.sleep(20);}
  ui(()->{MotionEvent e=MotionEvent.obtain(start,start+300,cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,x1,y1,0);session.dispatchStroke(e);e.recycle();return null;});Thread.sleep(tool==1?700:200);
 }
 void undo()throws Exception{ui(()->{session.undo();return null;});Thread.sleep(350);}
 void runProof()throws Exception{
  ready();Bitmap baseline=snap("00-baseline");check("source exact before annotation",diff(input,baseline)==0);check("source orientation corners",baseline.getPixel(5,5)==0xff20aa40&&baseline.getPixel(310,230)==0xffe5af00);
  stroke(0,Color.RED,25,55,295,55,.9f,false);Bitmap pen=snap("01-pen");check("genuine Input pen creates pixels",diff(baseline,pen)>500);check("pen stays in expected band",changed(baseline,pen,0,110,320,220)==0);check("real undo registered",ui(()->session.canUndo()));undo();Bitmap penUndone=snap("02-pen-undone");check("region Slice undo restores exact bytes",diff(baseline,penUndone)==0);
  stroke(0,Color.RED,30,80,135,80,.12f,false);stroke(0,Color.RED,185,80,290,80,.95f,false);Bitmap pressure=snap("03-stylus-pressure");int thin=changed(baseline,pressure,45,50,120,110),thick=changed(baseline,pressure,200,50,275,110);report.put("pressureThinPixels",thin).put("pressureThickPixels",thick);check("retained stylus pressure affects width",thin>0&&thick>thin*1.2);undo();undo();
  stroke(1,0xffff9600,35,155,270,155,.9f,false);Bitmap arrow=snap("04-stroke-arrow");check("stroke arrow upper arm",changed(baseline,arrow,190,110,275,145)>25);check("stroke arrow lower arm",changed(baseline,arrow,190,165,275,200)>25);undo();check("arrow region undo exact",diff(baseline,snap("05-arrow-undone"))==0);
  stroke(0,0x800000ff,30,115,290,115,.9f,false);Bitmap alpha=snap("06-alpha");int middle=alpha.getPixel(160,115);report.put("alphaPixel",Integer.toHexString(middle));check("upstream alpha composition",Color.red(middle)>80&&Color.red(middle)<220&&Color.blue(middle)>240);undo();
  stroke(0,Color.RED,30,115,280,115,.9f,true);check("ACTION_CANCEL leaves pixels unchanged",diff(baseline,snap("07-cancel"))==0);check("ACTION_CANCEL creates no undo",!ui(()->session.canUndo()));
  TextPaintView text=ui(()->session.addText("Café • 東京\nمرحبا",155,120,22,0xff102090));Thread.sleep(350);Bitmap plain=snap("08-unicode-text");check("plain Unicode text is flattened",diff(baseline,plain)>120);check("text has platform multiline layout",ui(()->text.getEditText().getLineCount())>=2);
  ui(()->{session.transformText(text,23,-16,1.25f,27);return null;});Thread.sleep(500);Bitmap transformed=snap("09-transformed-text");check("retained entity pan scale rotate changes pixels",diff(plain,transformed)>120);check("entity scale retained",Math.abs(ui(()->text.getScale())-1.25f)<.01);check("entity rotation retained",Math.abs(ui(()->text.getRotation())-27)<.01);undo();check("text transform undo restores rendered result",diff(plain,snap("10-text-transform-undone"))==0);undo();check("text insertion undo restores baseline",diff(baseline,snap("11-text-undone"))==0);
  stroke(0,Color.RED,35,60,270,75,.9f,false);Bitmap before=snap("12-before-recreation");
  for(int i=0;i<3;i++){RenderView rv=ui(()->session.retainedRenderView());int generation=ui(()->session.surfaceGeneration());ui(()->{session.removeView(rv);return null;});long end=SystemClock.uptimeMillis()+60000;while(!rv.getPainting().isPaused()){if(SystemClock.uptimeMillis()>end)throw new AssertionError("Pause did not snapshot");Thread.sleep(100);}Thread.sleep(350);ui(()->{session.addView(rv,1,new android.widget.FrameLayout.LayoutParams(320,240));return null;});end=SystemClock.uptimeMillis()+60000;while(ui(()->session.surfaceGeneration())<=generation){if(SystemClock.uptimeMillis()>end)throw new AssertionError("Surface recreation failed");Thread.sleep(100);}Thread.sleep(350);check("pause resume preserves pixels "+(i+1),diff(before,snap("13-recreated-"+i))==0);}
  Bitmap finalImage=snap("14-final-export");Bitmap reopened=BitmapFactory.decodeFile(new File(out,"14-final-export.png").getPath());check("PNG independently reopens exact pixels",reopened!=null&&reopened.getWidth()==320&&reopened.getHeight()==240&&diff(finalImage,reopened)==0);check("caller source remains unchanged",diff(input,baseline)==0);
  report.put("finalChangedPixels",diff(baseline,finalImage));check("manifest has no requested permissions",getPackageManager().getPackageInfo(getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions==null);
  ui(()->{session.close();return null;});Thread.sleep(500);check("close deletes owned region cache",getCacheDir().listFiles((dir,name)->name.startsWith("markup-")).length==0);
 }
 @Override protected void onDestroy(){if(session!=null)session.close();super.onDestroy();}
}
