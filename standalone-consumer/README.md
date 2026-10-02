# Independent note-attachment consumer

This is a self-contained Gradle source consumer with its own `settings.gradle`, wrapper, application, and copied `engine` source module. It does not depend on the feedback host, its APK, a prebuilt engine AAR, or directories outside this project. The copied source and notices are pinned in `PINNED-MODULE-SOURCE.json` by the root preparation script.

## Ordinary attachment workflow

1. Write a note in the local message-draft screen.
2. Choose **Attach note image**. The app captures its actual note-card view at measured pixels and puts that original image in the attachment slot. Dimensions outside 1–2048 pixels per side are rejected before allocation; no scaling decode or resampling occurs.
3. Choose **Annotate attachment**. This invokes a process-local editor overlay consuming only the public annotation API.
4. Draw a pen stroke or stroke-arrow. Add or edit a text label through the native Android `EditText` dialog. Tap/drag retained text, or use the explicit size/rotation controls. Undo is available.
5. Choose **Finish**. The editor waits for the retained engine's finish barrier, encodes the flattened bitmap as PNG, reopens it without scaling, checks source dimensions and pixel equality, and returns actual PNG bytes plus a preview to the attachment host.
6. The host replaces only the local attachment slot and reports the returned PNG dimensions and byte count. Nothing is sent to anyone.

**Cancel** and Back keep the prior attachment unchanged. A second edit starts from the current flattened attachment, so annotations from an earlier completed edit are image pixels, not a persistent editable project. Creating a new note attachment replaces the previous image only after capture succeeds.

## Integration contract

- Caller input stays owned by the host and is copied by `MarkupSession`
- The editor is measured at source size and its containing View is uniformly scaled and centered; source bitmap pixels are not altered
- `AnnotationEditor.Listener.onFinished(PngResult)` transfers byte-array and decoded-preview ownership to the host; it closes a prior result after clearing the old drawable
- The editor recycles intermediate export bitmaps and stale results; double Finish is ignored and controls are disabled while pending
- Cancel/destroy closes the session and prevents late callbacks from replacing the host result
- Engine/PNG failure leaves the attachment untouched and permits retry or Cancel
- Draft text is restored across Activity recreation, but session state and attachment bitmaps are process-local; the UI explicitly asks to attach again after recreation
- No Activity result URI, provider, shared file permission, network call, account, native code, bundled font or additional asset is needed

The ordinary host/editor sources import no retained renderer or text-entity classes. The editor adapter is deliberately small platform-only client code, duplicated here to make this project independently source-buildable. Both hosts call the same public `MarkupSession` interface and retain the same engine behavior.

## Build

Requires JDK 17+, Android SDK 35 and build-tools 35.0.0. Set `ANDROID_HOME` normally, then run `./gradlew --no-daemon --max-workers=1 :app:assembleDebug` from this directory. The root `tools/build-independent-consumer.sh` refreshes engine source and notices before building, while preserving this independent application.

Build prerequisites and verification scope are recorded in the root [BUILD.md](../BUILD.md) and [CHECKS.md](../CHECKS.md). The source module and example hosts remain experimental.
