#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,re,sys
r=Path(__file__).resolve().parents[1];files=json.loads((r/'provenance/module-source-lock.json').read_text())
for n,h in files.items():assert hashlib.sha256((r/n).read_bytes()).hexdigest()==h,('source changed since reviewed lock',n)
actual={str(p.relative_to(r))for p in (r/'engine').rglob('*')if p.is_file()and'/build/'not in str(p)}
assert actual==set(files),('untracked engine input',actual-set(files))
src='\n'.join(p.read_text()for p in (r/'engine/src/main/java').rglob('*.java'))
for forbidden in ['PAINT_SHAPE_FSH','ShapeDetector','ShapeInput','shapes.dat','Brush.Shape','BlurringShader','AnimatedEmoji','ApplicationLoader','UserConfig','ConnectionsManager','org.telegram','com.google.zxing','androidx.','paint_neon','paint_elliptical','createFromAsset']:
 assert forbidden not in src,('excluded dependency survived',forbidden)
assert 'implementation ' not in (r/'engine/build.gradle').read_text()
assets=list((r/'engine/src/main/res').rglob('*'));images=[p for p in assets if p.is_file()];assert len(images)==1
b=images[0].read_bytes();assert hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()=='6788001f9648e6f241b6a50474be5d78786e988a'
required=['Render.RenderPath(path, renderState, clearAll)','new Slice(getPaintingData(rect, true).data','delegate.requestUndoStore().registerUndo','GLES20.glReadPixels','egl10.eglCreateWindowSurface','smoothenAndPaintPoints','PRESSURE_INTERPOLATOR.getInterpolation(event.getPressure())','double leftCos = Math.cos(angle - Math.PI / 4 * 3.3)','currentCanvas.rotate(v.getRotation())','StaticLayout sl = new StaticLayout','deflater.deflate(buf)','inflater.inflate(output','undoRunnable.run()']
for needle in required:assert needle in src,('actual retained path missing',needle)
shader=(r/'engine/src/main/java/dev/oritwig/markup/core/ShaderSet.java').read_text()
assert re.findall(r'result.put\("([^"]+)", Collections.unmodifiableMap',shader)==['brush','blit','blitWithMask','compositeWithMask','nonPremultipliedBlit']
assert shader.count('shader = new HashMap<>();')==5
if (r/'upstream-private/ShaderSet.java').exists():
 original=(r/'upstream-private/ShaderSet.java').read_text()
 for name in re.findall(r'private static final String (PAINT_\w+)',shader):
  pat=rf'private static final String {name}\s*=[\s\S]*?";'
  assert re.search(pat,shader).group()==re.search(pat,original).group(),('shader changed',name)
for f in [r/'engine/src/main/AndroidManifest.xml',r/'smoke-host/src/main/AndroidManifest.xml']:
 assert 'uses-permission' not in f.read_text()
print('PASS locked module source, exact radial asset, retained execution chain, unchanged original shader strings, five registrations, zero external runtime dependencies/permissions, excluded closure absent')
