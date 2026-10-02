# Feedback screenshot host

The launcher is an ordinary local feedback form. Nothing is drawn or exported automatically.

1. Type a feedback draft and choose **Capture screen and annotate**.
2. The host draws its actual measured feedback view into an ARGB bitmap. Dimensions outside 1–2048 pixels per side are rejected before allocation; the bitmap is never resized.
3. A full-screen editor overlays the same Activity. Pen and stroke-arrow use the public `MarkupSession` API. **Add text** and **Edit text** use a native Android `EditText` dialog. Tap/drag retained text to select/move it; size and rotation controls operate on the selected retained entity. **Undo** uses the session undo store.
4. **Finish** waits for the engine completion callback, encodes its flattened source-size bitmap as PNG off the UI thread, decodes that PNG without scaling, checks dimensions and exact pixels, and returns the PNG bytes and preview through a process-local callback.
5. The feedback screen consumes that result by replacing its screenshot attachment preview and reporting its real PNG dimensions/byte count. The feedback draft is not submitted anywhere.

**Cancel** or Android Back returns without changing an existing attachment. Double Finish is ignored while the operation is pending. Editing is blocked during Finish, while Cancel remains available. Engine or PNG failures report no new image and allow retry/cancellation. Late export/encoding callbacks after cancellation or Activity destruction are discarded; their owned bitmaps are recycled.

## Ownership and lifecycle

`MainActivity` owns the screenshot input and keeps it alive until return/cancel. `MarkupSession` copies it. `AnnotationEditor.PngResult` transfers ownership of PNG bytes and its independently decoded preview only on `onFinished`. The host closes its previous result after clearing the preview drawable. No `ContentProvider`, shared file, URI, permission, account or external transmission is involved.

The session is measured at original source dimensions. A containing viewport scales and centers the **View** as a whole using equal X/Y scale and pivot `(0,0)`. The decoded input and engine source-pixel geometry are not resampled.

Activity destruction calls the editor's idempotent `dispose()`, closes the renderer and suppresses return callbacks. The text draft survives Activity recreation; an interrupted editor and returned images do not. The recreated form explicitly reports that no PNG was restored. This is an in-process workflow, not an editable project or persistence promise.

`ProofFixtureActivity` preserves the earlier deterministic renderer regression fixture as a non-exported, non-launcher Activity. It does not run as part of the normal host flow.

## Scope and checks

Only Android platform UI and the source `:engine` module are used. No network permissions, bundled fonts, added brush assets, provider or native code were introduced. The normal host/editor sources import only `dev.oritwig.markup.api.MarkupSession`; the separately retained regression fixture intentionally inspects internal rendering classes.

Build prerequisites and verification scope are recorded in the root [BUILD.md](../BUILD.md) and [CHECKS.md](../CHECKS.md). The source module and example hosts remain experimental.
