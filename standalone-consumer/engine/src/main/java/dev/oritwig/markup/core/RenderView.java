/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
package dev.oritwig.markup.core;

import dev.oritwig.markup.platform.Platform;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;

import dev.oritwig.markup.support.DispatchQueue;

import dev.oritwig.markup.support.Size;

import java.util.concurrent.CountDownLatch;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class RenderView extends TextureView {

    public interface RenderViewDelegate {
        void onBeganDrawing();
        void onFinishedDrawing(boolean moved);
        void onFirstDraw();
        default void onInputIdle() {}
        default void onSurfaceUnavailable() {}
        default void onRenderFailure() {}
        boolean shouldDraw();
        default void invalidateInputView() {}
        void resetBrush();
    }

    private RenderViewDelegate delegate;
    private UndoStore undoStore;
    private DispatchQueue queue;

    private Painting painting;
    private CanvasInternal internal;
    private Input input;

    private Bitmap bitmap;

    private boolean transformedBitmap;

    private boolean firstDrawSent;

    private float weight;
    private int color;
    private Brush brush;

    private volatile boolean shuttingDown;
    private boolean retiringSurface;
    private SurfaceTexture pendingSurface;
    private int pendingWidth, pendingHeight;
    private Runnable deferredClose;
    public void onRenderFailure(Throwable error) {
        Platform.log(error);
        Platform.runOnUIThread(() -> { if (!shuttingDown && delegate != null) delegate.onRenderFailure(); });
    }

    public boolean isInputIdle() { return input.isIdle(); }
    public boolean isSurfaceReady() { return !shuttingDown && !retiringSurface && internal != null && internal.ready && isAvailable(); }
    public void onInputIdle() { if (!shuttingDown && input.isIdle() && delegate != null) delegate.onInputIdle(); }
    private void createSurface(SurfaceTexture surface, int width, int height) {
        if (shuttingDown || surface == null || !isAvailable()) return;
        firstDrawSent = false;
        final CanvasInternal created = new CanvasInternal(surface);
        internal = created; created.setBufferSize(width, height); updateTransform();
        // Queue restoration before the first rendered/readable frame.
        if (painting.isPaused()) painting.onResume();
        created.requestRender();
    }

    public RenderView(Context context, Painting paint, Bitmap bitmap) {
        super(context);
        setOpaque(false);

        this.bitmap = bitmap;

        painting = paint;
        painting.setRenderView(this);

        setSurfaceTextureListener(new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (shuttingDown) return;
                if (retiringSurface) { pendingSurface = surface; pendingWidth = width; pendingHeight = height; return; }
                if (internal == null) createSurface(surface, width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                if (internal == null) {
                    return;
                }

                internal.setBufferSize(width, height);
                updateTransform();
                internal.requestRender();
                internal.postRunnable(() -> {
                    if (internal != null) {
                        internal.requestRender();
                    }
                });
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (surface == pendingSurface) { pendingSurface = null; return true; }
                final CanvasInternal old = internal;
                if (old == null || old.surfaceTexture != surface || retiringSurface || shuttingDown) return true;
                if (delegate != null) delegate.onSurfaceUnavailable();
                input.cancelForLifecycle();
                retiringSurface = true; old.ready = false;
                painting.onPause(() -> {
                    old.finish();
                    if (Looper.myLooper() != null) Looper.myLooper().quit();
                    Platform.runOnUIThread(() -> {
                        if (internal == old) internal = null;
                        retiringSurface = false;
                        Runnable completion = deferredClose; deferredClose = null;
                        if (completion != null) completion.run();
                        SurfaceTexture next = pendingSurface; pendingSurface = null;
                        if (next != null && !shuttingDown) createSurface(next, pendingWidth, pendingHeight);
                    });
                });

                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });

        input = new Input(this);
        painting.setDelegate(new Painting.PaintingDelegate() {
            @Override
            public void contentChanged() {
                if (internal != null) {
                    internal.scheduleRedraw();
                }
            }

            @Override
            public void strokeCommited() {

            }

            @Override
            public UndoStore requestUndoStore() {
                return undoStore;
            }

            @Override
            public DispatchQueue requestDispatchQueue() {
                return queue;
            }
        });
    }

    public void redraw() {
        if (internal == null) {
            return;
        }
        internal.requestRender();
    }

    public boolean onTouch(MotionEvent event) {
        if (shuttingDown || retiringSurface || event.getPointerCount() > 1) {
            return false;
        }
        if (internal == null || !internal.initialized || !internal.ready) {
            return true;
        }
        input.process(event, getScaleX());
        return true;
    }





    public void setUndoStore(UndoStore store) {
        undoStore = store;
    }

    public void setQueue(DispatchQueue dispatchQueue) {
        queue = dispatchQueue;
    }

    public void setDelegate(RenderViewDelegate renderViewDelegate) {
        delegate = renderViewDelegate;
    }

    public Painting getPainting() {
        return painting;
    }

    public float brushWeightForSize(float size) {
        float paintingWidth = painting.getSize().width;
        return 8.0f / 2048.0f * paintingWidth + (90.0f / 2048.0f * paintingWidth) * size;
    }

    public int getCurrentColor() {
        return color;
    }

    public void setColor(int value) {
        color = value;

    }

    public float getCurrentWeight() {
        return weight;
    }

    public void setBrushSize(float size) {
        weight = brushWeightForSize(size);

    }

    public Brush getCurrentBrush() {
        return brush;
    }

    public UndoStore getUndoStore() {
        return undoStore;
    }

    public void setBrush(Brush value) {

        brush = value;
        updateTransform();
        painting.setBrush(brush);

    }

    public void resetBrush() {
        if (delegate != null) {
            delegate.resetBrush();
        }
        input.ignoreOnce();
    }



    private void updateTransform() {
        if (internal == null) {
            return;
        }
        Matrix matrix = new Matrix();

        float scale = painting != null ? getWidth() / painting.getSize().width : 1.0f;
        if (scale <= 0) {
            scale = 1.0f;
        }

        Size paintingSize = getPainting().getSize();

        matrix.preTranslate(getWidth() / 2.0f, getHeight() / 2.0f);
        matrix.preScale(scale, -scale);
        matrix.preTranslate(-paintingSize.width / 2.0f, -paintingSize.height / 2.0f);

        input.setMatrix(matrix);

        float[] proj = GLMatrix.LoadOrtho(0.0f, internal.bufferWidth, 0.0f, internal.bufferHeight, -1.0f, 1.0f);
        float[] effectiveProjection = GLMatrix.LoadGraphicsMatrix(matrix);
        float[] finalProjection = GLMatrix.MultiplyMat4f(proj, effectiveProjection);
        painting.setRenderProjection(finalProjection);
    }

    public boolean shouldDraw() {
        return delegate == null || delegate.shouldDraw();
    }

    public void onBeganDrawing() {
        if (delegate != null) {
            delegate.onBeganDrawing();
        }
    }

    public void onFinishedDrawing(boolean moved) {
        if (delegate != null) {
            delegate.onFinishedDrawing(moved);
        }
    }

    public void shutdown() { shutdown(null); }

    public void shutdown(Runnable completion) {
        if (shuttingDown) { if (completion != null) Platform.runOnUIThread(completion); return; }
        input.cancelForLifecycle(); shuttingDown = true; pendingSurface = null;
        final CanvasInternal old = internal;
        if (retiringSurface) { deferredClose = completion; setVisibility(View.GONE); return; }
        if (old != null) {
            // Post directly: cleanup completion must run even when EGL initialization failed.
            old.postRunnable(() -> {
                if (old.initialized && old.setCurrentContext()) painting.cleanResources(transformedBitmap);
                old.finish();
                if (Looper.myLooper() != null) Looper.myLooper().quit();
                Platform.runOnUIThread(() -> {
                    if (internal == old) internal = null;
                    if (completion != null) completion.run();
                });
            });
        } else if (completion != null) Platform.runOnUIThread(completion);
        setVisibility(View.GONE);
    }



    private class CanvasInternal extends DispatchQueue {
        private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private static final int EGL_OPENGL_ES2_BIT = 4;
        private SurfaceTexture surfaceTexture;
        private EGL10 egl10;
        private EGLDisplay eglDisplay;
        private EGLContext eglContext;
        private EGLSurface eglSurface;
        private volatile boolean initialized;
        private volatile boolean ready;

        private int bufferWidth;
        private int bufferHeight;

        private long lastRenderCallTime;
        private Runnable scheduledRunnable;



        public CanvasInternal(SurfaceTexture surface) {
            super("CanvasInternal", false);

            surfaceTexture = surface;
            start();
        }

        @Override
        public void run() {
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }

            try {
                initialized = initGL();
                if (!initialized) onRenderFailure(new IllegalStateException("EGL initialization failed"));
            } catch (RuntimeException failure) {
                initialized = false; onRenderFailure(failure);
            }
            // Keep the cleanup-capable queue even when EGL failed to initialize.
            super.run();
        }

        private boolean initGL() {
            egl10 = (EGL10) EGLContext.getEGL();

            eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                if (true) {
                    Platform.log("eglGetDisplay failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            int[] version = new int[2];
            if (!egl10.eglInitialize(eglDisplay, version)) {
                if (true) {
                    Platform.log("eglInitialize failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            int[] configsCount = new int[1];
            EGLConfig[] configs = new EGLConfig[1];
            int[] configSpec = new int[]{
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 0,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_NONE
            };
            EGLConfig eglConfig;
            if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
                if (true) {
                    Platform.log("eglChooseConfig failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            } else if (configsCount[0] > 0) {
                eglConfig = configs[0];
            } else {
                if (true) {
                    Platform.log("eglConfig not initialized");
                }
                finish();
                return false;
            }

            int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
            EGLContext parentContext = EGL10.EGL_NO_CONTEXT;
            eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, parentContext, attrib_list);
            if (eglContext == null) {
                if (true) {
                    Platform.log("eglCreateContext failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }
            

            if (surfaceTexture instanceof SurfaceTexture) {
                eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
            } else {
                finish();
                return false;
            }

            if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
                if (true) {
                    Platform.log("createWindowSurface failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }
            if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                if (true) {
                    Platform.log("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glDisable(GLES20.GL_DITHER);
            GLES20.glDisable(GLES20.GL_STENCIL_TEST);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);

            painting.setupShaders();
            checkBitmap();
            painting.setBitmap(bitmap);

            Utils.HasGLError();

            return true;
        }

        private Bitmap createBitmap(Bitmap bitmap, float scale) {
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        private void checkBitmap() {
            Size paintingSize = painting.getSize();
            if (bitmap.getWidth() != paintingSize.width || bitmap.getHeight() != paintingSize.height) {
                Bitmap b = Bitmap.createBitmap((int) paintingSize.width, (int) paintingSize.height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(b);
                canvas.drawBitmap(bitmap, null, new RectF(0, 0, paintingSize.width, paintingSize.height), null);
                bitmap = b;
                transformedBitmap = true;
            }

        }

        private boolean setCurrentContext() {
            if (!initialized) {
                return false;
            }

            if (!eglContext.equals(egl10.eglGetCurrentContext()) || !eglSurface.equals(egl10.eglGetCurrentSurface(EGL10.EGL_DRAW))) {
                if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    return false;
                }
            }
            return true;
        }

        private Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {
                if (!initialized || shuttingDown) {
                    return;
                }

                setCurrentContext();

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                GLES20.glViewport(0, 0, bufferWidth, bufferHeight);

                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                painting.render();

                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

                egl10.eglSwapBuffers(eglDisplay, eglSurface);
                if (!firstDrawSent) {
                    firstDrawSent = true;
                    Platform.runOnUIThread(() -> {
                        if (!shuttingDown && !retiringSurface && internal == CanvasInternal.this && delegate != null) delegate.onFirstDraw();
                    });
                }

                if (!ready) {
                    ready = true;
                }
            }
        };

        public void setBufferSize(int width, int height) {
            bufferWidth = width;
            bufferHeight = height;
        }

        public void requestRender() {
            postRunnable(drawRunnable);
        }

        public Runnable safeRequestRender = () -> {
            if (scheduledRunnable != null) {
                cancelRunnable(scheduledRunnable);
                scheduledRunnable = null;
            }
            cancelRunnable(drawRunnable);
            postRunnable(drawRunnable);
        };

        public void scheduleRedraw() {
            if (scheduledRunnable != null) {
                cancelRunnable(scheduledRunnable);
                scheduledRunnable = null;
            }

            scheduledRunnable = () -> {
                scheduledRunnable = null;
                drawRunnable.run();
            };

            postRunnable(scheduledRunnable, 1);
        }

        public void finish() {
            if (eglSurface != null) {
                egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                egl10.eglDestroySurface(eglDisplay, eglSurface);
                eglSurface = null;
            }
            if (eglContext != null) {
                
                egl10.eglDestroyContext(eglDisplay, eglContext);
                eglContext = null;
            }
            if (eglDisplay != null) {
                egl10.eglTerminate(eglDisplay);
                eglDisplay = null;
            }
            
        }

        public void shutdown() {
            postRunnable(() -> {
                finish();
                Looper looper = Looper.myLooper();
                if (looper != null) {
                    looper.quit();
                }
            });
        }

        public Bitmap getTexture() {
            return getTextureInternal();
        }

        private Bitmap getTextureInternal() {
            if (!initialized) {
                return null;
            }
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final Bitmap[] object = new Bitmap[1];
            try {
                postRunnable(() -> {
                    try {
                        Painting.PaintingData data = painting.getPaintingData(new RectF(0, 0, painting.getSize().width, painting.getSize().height), false);
                        if (data != null) object[0] = data.bitmap;
                    } catch (RuntimeException failure) {
                        onRenderFailure(failure);
                    } finally {
                        countDownLatch.countDown();
                    }
                });
                if (!countDownLatch.await(30, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("GL readback timed out");
            } catch (Exception e) {
                Platform.log(e);
            }
            return object[0];
        }
    }

    public interface BitmapCallback { void complete(Bitmap bitmap); }
    /** Queue-bound readback; callback always returns to main without blocking input/cancel. */
    public void getResultBitmapAsync(BitmapCallback callback) {
        final CanvasInternal target = internal;
        if (target == null || shuttingDown || !target.ready) { callback.complete(null); return; }
        target.postRunnable(() -> {
            Bitmap result = null;
            try {
                if (target.initialized && target.setCurrentContext()) {
                    Painting.PaintingData data = painting.getPaintingData(new RectF(0, 0, painting.getSize().width, painting.getSize().height), false);
                    if (data != null) result = data.bitmap;
                }
            } catch (RuntimeException failure) { onRenderFailure(failure); }
            final Bitmap answer = result;
            Platform.runOnUIThread(() -> callback.complete(answer));
        });
    }

    public Bitmap getResultBitmap() {

        return internal != null ? internal.getTexture() : null;
    }

    public void performInContext(final Runnable action) { performInContext(action, null); }
    private void performInContextFailure(Runnable failureCompletion) {
        onRenderFailure(new IllegalStateException("GL context unavailable"));
        if (failureCompletion != null) failureCompletion.run();
    }
    public void performInContext(final Runnable action, final Runnable failureCompletion) {
        final CanvasInternal target = internal;
        if (target == null) { performInContextFailure(failureCompletion); return; }
        target.postRunnable(() -> {
            if (!target.initialized || !target.setCurrentContext()) { performInContextFailure(failureCompletion); return; }
            try { action.run(); }
            catch (RuntimeException failure) { onRenderFailure(failure); }
        });
    }



    protected int dp(float value) { return Platform.dp(getContext(), value); }
    protected float dpf2(float value) { return Platform.dpf2(getContext(), value); }
}
