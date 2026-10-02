/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
package dev.oritwig.markup.core;

import dev.oritwig.markup.platform.Platform;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.opengl.GLES20;




import dev.oritwig.markup.support.DispatchQueue;


import dev.oritwig.markup.support.CubicBezierInterpolator;
import dev.oritwig.markup.support.Size;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.microedition.khronos.opengles.GL10;

public class Painting {

    public interface PaintingDelegate {
        void contentChanged();
        void strokeCommited();
        UndoStore requestUndoStore();
        DispatchQueue requestDispatchQueue();
    }

    public static class PaintingData {
        public Bitmap bitmap;
        public ByteBuffer data;

        PaintingData(Bitmap b, ByteBuffer buffer) {
            bitmap = b;
            data = buffer;
        }
    }

    private final java.io.File cacheDir;
    private final android.content.res.Resources resources;
    private PaintingDelegate delegate;
    private Path activePath;


    private RenderState renderState;
    private RenderView renderView;
    private Size size;
    private RectF activeStrokeBounds;
    private Brush brush;
    private HashMap<Integer, Texture> brushTextures = new HashMap<>();
    private Texture bitmapTexture;

    private ByteBuffer vertexBuffer;
    private ByteBuffer textureBuffer;
    private int reusableFramebuffer;
    private int paintTexture;

    private Map<String, Shader> shaders;
    private int suppressChangesCounter;
    private int[] buffers = new int[1];
    private ByteBuffer dataBuffer;

    private boolean paused;
    private Slice backupSlice;

    private float[] projection;
    private float[] renderProjection;











    public Painting(Size sz, java.io.File cacheDir, android.content.res.Resources resources) {
        renderState = new RenderState();
        this.cacheDir = cacheDir;
        this.resources = resources;


        size = sz;



        dataBuffer = ByteBuffer.allocateDirect((int) size.width * (int) size.height * 4);

        projection = GLMatrix.LoadOrtho(0, size.width, 0, size.height, -1.0f, 1.0f);

        if (vertexBuffer == null) {
            vertexBuffer = ByteBuffer.allocateDirect(8 * 4);
            vertexBuffer.order(ByteOrder.nativeOrder());
        }
        vertexBuffer.putFloat(0.0f);
        vertexBuffer.putFloat(0.0f);
        vertexBuffer.putFloat(size.width);
        vertexBuffer.putFloat(0.0f);
        vertexBuffer.putFloat(0.0f);
        vertexBuffer.putFloat(size.height);
        vertexBuffer.putFloat(size.width);
        vertexBuffer.putFloat(size.height);
        vertexBuffer.rewind();

        if (textureBuffer == null) {
            textureBuffer = ByteBuffer.allocateDirect(8 * 4);
            textureBuffer.order(ByteOrder.nativeOrder());
            textureBuffer.putFloat(0.0f);
            textureBuffer.putFloat(0.0f);
            textureBuffer.putFloat(1.0f);
            textureBuffer.putFloat(0.0f);
            textureBuffer.putFloat(0.0f);
            textureBuffer.putFloat(1.0f);
            textureBuffer.putFloat(1.0f);
            textureBuffer.putFloat(1.0f);
            textureBuffer.rewind();
        }
    }





    public void setDelegate(PaintingDelegate paintingDelegate) {
        delegate = paintingDelegate;
    }

    public void setRenderView(RenderView view) {
        renderView = view;
    }

    public Size getSize() {
        return size;
    }

    public RectF getBounds() {
        return new RectF(0.0f, 0.0f, size.width, size.height);
    }

    private boolean isSuppressingChanges() {
        return suppressChangesCounter > 0;
    }

    private void beginSuppressingChanges() {
        suppressChangesCounter++;
    }

    private void endSuppressingChanges() {
        suppressChangesCounter--;
    }

    public void setBitmap(Bitmap bitmap) {
        if (bitmapTexture == null) {
            bitmapTexture = new Texture(bitmap);
        }
    }


    public void paintStroke(final Path path, final boolean clearBuffer, final boolean clearAll, final Runnable action) {

        renderView.performInContext(() -> {
            paintStrokeInternal(path, clearBuffer, clearAll);

            if (action != null) {
                action.run();
            }
        });
    }

    private void paintStrokeInternal(final Path path, final boolean clearBuffer, final boolean clearAll) {
        activePath = path;
        if (path == null) {
            return;
        }

        RectF bounds = null;

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getReusableFramebuffer());
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, getPaintTexture(), 0);

        Utils.HasGLError();

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glViewport(0, 0, (int) size.width, (int) size.height);

            if (clearBuffer) {
                GLES20.glClearColor(0, 0, 0, 0);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            }

            if (shaders == null) {
                return;
            }

            Brush brush = path.getBrush();
            Shader shader = shaders.get(brush.getShaderName(Brush.PAINT_TYPE_BRUSH));
            if (shader == null) {
                return;
            }

            GLES20.glUseProgram(shader.program);
            Texture brushTexture = brushTextures.get(brush.getStampResId());
            if (brushTexture == null) {
                brushTexture = new Texture(brush.getStamp(resources));
                brushTextures.put(brush.getStampResId(), brushTexture);
            }
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, brushTexture.texture());
            GLES20.glUniformMatrix4fv(shader.getUniform("mvpMatrix"), 1, false, FloatBuffer.wrap(projection));
            GLES20.glUniform1i(shader.getUniform("texture"), 0);

            if (!clearAll) {
                renderState.viewportScale = renderView.getScaleX();
            } else {
                renderState.viewportScale = 1f;
            }
            // Brush-pass state boundary: export/commit use replacement blending.
            // Do not depend on a display frame running between readback and input.
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            bounds = Render.RenderPath(path, renderState, clearAll);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        if (delegate != null) {
            delegate.contentChanged();
        }

        if (activeStrokeBounds != null) {
            activeStrokeBounds.union(bounds);
        } else {
            activeStrokeBounds = bounds;
        }
    }

    public void commitPath(final Path path, final int color) {
        commitPath(path, color, true, null);
    }

    public void commitPath(final Path path, final int color, final boolean registerUndo, Runnable action) {
        if (shaders == null || brush == null) {
            return;
        }
        renderView.performInContext(() -> {
            commitPathInternal(path, color, registerUndo ? activeStrokeBounds : null);

            if (registerUndo) {
                activeStrokeBounds = null;
            }

            if (action != null) {
                action.run();
            }
        });
    }

    private Slice commitPathInternal(final Path path, final int color, RectF bounds) {
        Brush brush = this.brush;
        if (path != null) {
            brush = path.getBrush();
        }

        Slice undoSlice = registerUndo(bounds, false);

        beginSuppressingChanges();

        int count = 1;

        for (int a = 0; a < count; ++a) {

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getReusableFramebuffer());
            int tex = getTexture();


            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex, 0);

            GLES20.glViewport(0, 0, (int) size.width, (int) size.height);

            Shader shader = shaders.get(brush.getShaderName(Brush.PAINT_TYPE_COMPOSITE));
            if (shader == null) {
                return null;
            }

            GLES20.glUseProgram(shader.program);

            GLES20.glUniformMatrix4fv(shader.getUniform("mvpMatrix"), 1, false, FloatBuffer.wrap(projection));
            GLES20.glUniform1i(shader.getUniform("texture"), 0);
            GLES20.glUniform1i(shader.getUniform("mask"), 1);
            Shader.SetColorUniform(shader.getUniform("color"), Platform.setAlphaComponent(color, (int) (Color.alpha(color) * brush.getOverrideAlpha())));

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getPaintTexture());

            Object lock = null;


            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ZERO);

            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
            GLES20.glEnableVertexAttribArray(0);
            GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(1);

            if (lock != null) {
                synchronized (lock) {
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                }
            } else {
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getTexture());
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        if (!isSuppressingChanges() && delegate != null) {
            delegate.contentChanged();
        }

        endSuppressingChanges();
        renderState.reset();

        activePath = null;


        return undoSlice;
    }

    public void clearStroke() {
        clearStroke(null);
    }

    public void clearStroke(Runnable action) {
        renderView.performInContext(() -> {
            clearStrokeInternal();

            if (action != null) {
                action.run();
            }
        });
    }

    private void clearStrokeInternal() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getReusableFramebuffer());
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, getPaintTexture(), 0);

        Utils.HasGLError();

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glViewport(0, 0, (int) size.width, (int) size.height);
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        if (delegate != null) {
            delegate.contentChanged();
        }
        renderState.reset();
        activeStrokeBounds = null;
        activePath = null;


    }

    private Slice registerUndo(RectF rect, boolean blurTex) {
        if (rect == null) {
            return null;
        }

        boolean intersect = rect.setIntersect(rect, getBounds());
        if (!intersect) {
            return null;
        }

        final Slice slice = new Slice(getPaintingData(rect, true).data, 0, rect, cacheDir);
        delegate.requestUndoStore().registerUndo(UUID.randomUUID(), () -> restoreSlice(slice));

        return slice;
    }

    private void restoreSlice(final Slice slice) {
        renderView.performInContext(() -> {
            try { restoreSliceInternal(slice, true); }
            catch (RuntimeException failure) { renderView.onRenderFailure(failure); }
        });
    }

    private void restoreSliceInternal(final Slice slice, boolean forget) {
        if (slice == null) {
            return;
        }

        ByteBuffer buffer = slice.getData();

        if (buffer == null || buffer.remaining() != slice.getWidth() * slice.getHeight() * 4) {
            throw new IllegalStateException("Invalid layer snapshot");
        }
        int tex = getTexture();

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, slice.getX(), slice.getY(), slice.getWidth(), slice.getHeight(), GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
        if (!isSuppressingChanges() && delegate != null) {
            delegate.contentChanged();
        }

        if (forget) {
            slice.cleanResources();
        }
    }

    public void setRenderProjection(float[] proj) {
        renderProjection = proj;
    }

    public void render() {
        if (shaders == null) {
            return;
        }



        if (activePath != null) {
            renderBlitPath(getPaintTexture(), activePath, 1f);

        } else {
            renderBlit(getTexture(), 1f);
        }


    }

    private void renderBlitPath(int mask, Path path, float alpha) {
        if (path == null) {
            return;
        }
        Brush brush = path.getBrush();
        if (brush == null) {
            brush = this.brush;
        }


        Shader shader = shaders.get(brush.getShaderName(Brush.PAINT_TYPE_BLIT));
        if (shader == null) {
            return;
        }

        GLES20.glUseProgram(shader.program);

        GLES20.glUniformMatrix4fv(shader.getUniform("mvpMatrix"), 1, false, FloatBuffer.wrap(renderProjection));
        GLES20.glUniform1i(shader.getUniform("texture"), 0);
        GLES20.glUniform1i(shader.getUniform("mask"), 1);
        int color = path.getColor();
        color = Platform.setAlphaComponent(color, (int) (Color.alpha(color) * brush.getOverrideAlpha() * alpha));
        Shader.SetColorUniform(shader.getUniform("color"), color);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getTexture());

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mask);



        Object lock = null;

        
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
        GLES20.glEnableVertexAttribArray(1);

        if (lock != null) {
            synchronized (lock) {
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            }
        } else {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        Utils.HasGLError();
    }

    private void renderBlit(int texture, float alpha) {
        Shader shader = shaders.get("blit");
        if (texture == 0 || shader == null) {
            return;
        }

        GLES20.glUseProgram(shader.program);

        GLES20.glUniformMatrix4fv(shader.getUniform("mvpMatrix"), 1, false, FloatBuffer.wrap(renderProjection));
        GLES20.glUniform1f(shader.getUniform("alpha"), alpha);


            GLES20.glUniform1i(shader.getUniform("texture"), 0);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);

        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
        GLES20.glEnableVertexAttribArray(1);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        Utils.HasGLError();
    }

    public PaintingData getPaintingData(RectF rect, boolean undo) {
        int minX = (int) rect.left;
        int minY = (int) rect.top;
        int width = (int) rect.width();
        int height = (int) rect.height();

        if (undo) {
            // Lifecycle/undo snapshots preserve exact texture bytes. A shader round-trip
            // can round bytes when sampling GL_LINEAR; exact recreation remains tested.
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getReusableFramebuffer());
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, getTexture(), 0);
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                throw new IllegalStateException("Snapshot framebuffer incomplete");
            }
            dataBuffer.clear(); dataBuffer.limit(width * height * 4);
            GLES20.glReadPixels(minX, minY, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, dataBuffer);
            int error = GLES20.glGetError();
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            if (error != GLES20.GL_NO_ERROR) throw new IllegalStateException("Snapshot readback failed: " + error);
            dataBuffer.rewind();
            return new PaintingData(null, dataBuffer);
        }

        GLES20.glGenFramebuffers(1, buffers, 0);
        int framebuffer = buffers[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);

        GLES20.glGenTextures(1, buffers, 0);
        int texture = buffers[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0);

        GLES20.glViewport(0, 0, (int) size.width, (int) size.height);

        if (shaders == null) {
            return null;
        }
        Shader shader = shaders.get(undo ? "nonPremultipliedBlit" : ("blit"));
        if (shader == null) {
            return null;
        }
        GLES20.glUseProgram(shader.program);

        Matrix translate = new Matrix();
        translate.preTranslate(-minX, -minY);
        float[] effective = GLMatrix.LoadGraphicsMatrix(translate);
        float[] finalProjection = GLMatrix.MultiplyMat4f(projection, effective);

        GLES20.glUniformMatrix4fv(shader.getUniform("mvpMatrix"), 1, false, FloatBuffer.wrap(finalProjection));


            GLES20.glUniform1i(shader.getUniform("texture"), 0);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getTexture());

        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ZERO);

        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
        GLES20.glEnableVertexAttribArray(1);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);



        dataBuffer.limit(width * height * 4);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, dataBuffer);

        PaintingData data;
        if (undo) {
            data = new PaintingData(null, dataBuffer);
        } else {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(dataBuffer);

            data = new PaintingData(bitmap, null);
        }

        dataBuffer.rewind();

        buffers[0] = framebuffer;
        GLES20.glDeleteFramebuffers(1, buffers, 0);

        buffers[0] = texture;
        GLES20.glDeleteTextures(1, buffers, 0);

        return data;
    }

    public boolean isPaused() {
        return paused;
    }

    public void onPause(final Runnable completionRunnable) {
        renderView.performInContext(() -> {
            try {
                PaintingData data = getPaintingData(getBounds(), true);
                Slice snapshot = new Slice(data.data, 0, getBounds(), cacheDir);
                ByteBuffer verified = snapshot.getData();
                if (verified == null || verified.remaining() != (int) size.width * (int) size.height * 4)
                    throw new IllegalStateException("Cannot preserve layer snapshot");
                backupSlice = snapshot; paused = true;
                cleanResources(false);
            } catch (RuntimeException failure) {
                renderView.onRenderFailure(failure);
                // No export succeeds after a failed snapshot; release EGL in all cases.
                cleanResources(false);
            } finally {
                if (completionRunnable != null) completionRunnable.run();
            }
        }, completionRunnable);
    }

    public void onResume() {
        restoreSlice(backupSlice);
        backupSlice = null;
        paused = false;
    }

    public void cleanResources(boolean recycle) {
        if (reusableFramebuffer != 0) {
            buffers[0] = reusableFramebuffer;
            GLES20.glDeleteFramebuffers(1, buffers, 0);
            reusableFramebuffer = 0;
        }

        if (bitmapTexture != null) {
            bitmapTexture.cleanResources(recycle);
        }


        if (paintTexture != 0) {
            buffers[0] = paintTexture;
            GLES20.glDeleteTextures(1, buffers, 0);
            paintTexture = 0;
        }

        for (Texture texture : brushTextures.values()) {
            if (texture != null) {
                texture.cleanResources(true);
            }
        }
        brushTextures.clear();






        if (shaders != null) {
            for (Shader shader : shaders.values()) {
                shader.cleanResources();
            }
            shaders = null;
        }
    }

    private int getReusableFramebuffer() {
        if (reusableFramebuffer == 0) {
            int[] buffers = new int[1];
            GLES20.glGenFramebuffers(1, buffers, 0);
            reusableFramebuffer = buffers[0];

            Utils.HasGLError();
        }
        return reusableFramebuffer;
    }

    private int getTexture() {
        if (bitmapTexture != null) {
            return bitmapTexture.texture();
        }
        return 0;
    }

    private int getPaintTexture() {
        if (paintTexture == 0) {
            paintTexture = Texture.generateTexture(size);
        }
        return paintTexture;
    }

    public void setupShaders() {
        shaders = ShaderSet.setup();
    }

    public void setBrush(Brush brush) { this.brush = brush; }
}

