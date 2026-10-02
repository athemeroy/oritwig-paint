/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
package dev.oritwig.markup.api;
import android.graphics.*;
import android.view.View;
import dev.oritwig.markup.core.views.*;
/** Telegram LPhotoPaintView.getBitmap, lines 1761–1772 and 1898–1933 at the pinned source.
 * Retains the original plain-text Canvas transform and offscreen-text composition path.
 * Removed video entity serialization, animated stickers, thumbnails and account branches. */
final class EntityComposition {
 static void draw(Bitmap bitmap, EntitiesContainerView entitiesView) {
  if (bitmap != null && entitiesView.entitiesCount() > 0) {
   int count = entitiesView.getChildCount();
   for (int i = 0; i < count; i++) {
    View v = entitiesView.getChildAt(i);
    if (!(v instanceof EntityView)) continue;
    EntityView entity = (EntityView)v;
    PointF position = new PointF(entity.getX() + entity.getWidth()/2f, entity.getY() + entity.getHeight()/2f);
    Canvas currentCanvas = new Canvas(bitmap);
    currentCanvas.save();
    currentCanvas.translate(position.x, position.y);
    currentCanvas.scale(v.getScaleX(), v.getScaleY());
    currentCanvas.rotate(v.getRotation());
    currentCanvas.translate(-entity.getWidth()/2f, -entity.getHeight()/2f);
    if (v instanceof TextPaintView && v.getHeight()>0 && v.getWidth()>0) {
     Bitmap b = Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);
     Canvas c = new Canvas(b);
     entity.drawForExport(c);
     currentCanvas.drawBitmap(b,null,new Rect(0,0,b.getWidth(),b.getHeight()),null);
     c.setBitmap(null);
     b.recycle();
    } else entity.drawForExport(currentCanvas);
    currentCanvas.restore();
   }
  }
 }
}
