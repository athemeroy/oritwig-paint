# Module integration contract

## Ordinary host API

`new MarkupSession(Context, Bitmap, File callerCacheDirectory)` creates one view/session. All API use is on the main thread. Caller retains input ownership. At most 2048×2048, at least 1×1; rejected inputs are never silently resized.

- `imageWidth()`, `imageHeight()` retain source-pixel dimensions
- `isReady()` means a current usable GL surface; `isIdle()` additionally means no active gesture or pending stroke commit
- `whenReady(Runnable)` is a persistent readiness listener; a reattached surface may call it again
- `whenIdle(IdleCallback)` is one-shot success/failure-aware; `onIdle()` and `onFailure(Failure)` run on main. Compatibility Runnable overload is cancelled on failure/close
- `setFailureListener(FailureListener)` reports permanent renderer failure; host should keep Cancel accessible and reopen with a new session
- `selectDrawingTool(Tool.PEN | Tool.STROKE_ARROW)` selects drawing and deselects text
- `setColor(int)`, `setWeight(float 0..1)` require no pending stroke/gesture
- `addLabel(String, x, y, fontSize, color)` adds and selects a label; text max 4096 UTF-16 code units, font size 8..256 source pixels
- `selectedTextValue()` returns text or null; `editSelectedText(String)` is undoable
- `transformSelectedText(dx,dy,scaleFactor,angleDelta)` is undoable; real hit-tested body/side-handle/container gestures are transactionally undoable too
- `canUndo()`, `undo()` reject undo during a pending stroke/gesture; hosts may gate tool actions on isIdle
- `finish(ExportCallback)` locks edits, waits for actual GL commit, asynchronously reads pixels, composes settled text, then calls exactly one `onExport(Bitmap)` or `onFailure(Failure)`. The result bitmap belongs to the caller. Successful export unlocks before callback, permitting retry if host PNG I/O fails
- `close()` is idempotent. It fails a pending Finish, cancels stale callbacks, serializes renderer cleanup, deletes only the private session cache directory, and leaves caller input untouched

The host example encodes the bitmap to PNG on a background executor, checks its bounds, decodes without resampling, checks exact pixels, then returns both PNG bytes and preview to its ordinary parent. It closes the session only after success/cancel. No Activity-result URI, ContentProvider, persistent grant or recipient sharing is involved in this same-process contract.

## Failure states

- `SESSION_CLOSED`: Finish after close, or close while waiting
- `SURFACE_UNAVAILABLE`: no usable surface, or surface removed during pending Finish
- `GESTURE_IN_PROGRESS`: a hit-tested gesture is still down when Finish is requested
- `EXPORT_IN_PROGRESS`: another Finish is pending
- `COMMIT_TIMEOUT`: no successful completion within 10 seconds; late readback is recycled, not delivered
- `PIXEL_READBACK_FAILED`: readback/composition could not produce output
- `RENDERER_FAILED`: initialization/context/snapshot failure; session cannot resume safe export, so Cancel and reopen

Mutation methods throw for a closed session, invalid input, active gesture, pending stroke, or locked Finish. Legacy synchronous `exportBitmap()/writePng()` explicitly reject non-idle state. They remain diagnostic/compatibility methods and can block on the old synchronous GL readback; interactive hosts use `finish`.

## Compatibility and diagnostics

`addText`, `transformText`, `select`, `retainedRenderView`, `retainedEntitiesView`, `dispatchStroke`, `sourceToScreen`, `screenToSource` remain available for the existing fixtures and legacy consumers. Several expose retained core types. Ordinary host code does not import those types. No model/entity serialization or stable internal-class ABI is promised.

## Lifecycle limits

Surface detachment/reattachment of the same live session preserves committed raster data using exact texture snapshots. An active in-flight stroke is cancelled on surface loss. Activity/process recreation is an interrupted edit and the example host says so; it does not claim persisted editable state. System-font text varies across devices. API 26 does not support retained two-pointer text transforms; API 29+ requires separate runtime verification.
