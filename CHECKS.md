# Verification scope

## Recorded build and runtime evidence

The tested source lock is `cedfd27eff91326c4fbb3aea684d78b5b68f95db64b246514a815123d0a9da72`. All 37 engine inputs and the independently buildable copy match. Engine, fixture and build inputs remain identical. Two host editor files have a [publication-source delta](provenance/publication-source-delta.json): unchanged background PNG encoding/validation was moved into a private static non-View helper to correct lint thread inference. The executor, ownership and callbacks are preserved. Recorded ordinary-device checks used the earlier host source; this extraction does not imply a new ordinary-device runtime test.

- Engine, feedback host and independent attachment host compiled with the [recorded toolchain](BUILD.md), offline, one worker and a 1 GiB Gradle heap
- Engine lint: 0 errors, 14 retained programmatic-view/accessibility/API warnings; no new suppressions
- Deterministic Java host-core checks: 20 passed
- Five shader strings and sole radial resource unchanged; APK manifests request no permissions and contain no native `.so` files

The exact test binaries are identified in [provenance/tested-artifacts.json](provenance/tested-artifacts.json); compiled binaries are not committed.

## API 26 synthetic fixture

**272 supported checks passed, 0 failed, 2 unsupported**, using the API 26 x86 software-rendered emulator at 1080×1920 / 420 dpi. The final fixture source hash is `c6710171222472632ec93c43b0cefad100c6d36885ab515f764fe2d5c0318941`.

Covered: body, selected-body, side-handle and container gestures; undo and CANCEL rollback at 1×/0.5× host scale; source-coordinate mapping; editable-text undo; pen, within-stroke pressure, curved arrow, alpha and cancellation; immediate commit-aware Finish; duplicate Finish; close with pending result; source bounds and caller-input immutability; exact PNG reopen; owned-cache cleanup.

Three same-session surface recreation comparisons had **0 changed pixels**. All pixel checks use zero tolerance. API 26 two-pointer text transforms and the absent plain-text whole-handle hit region were unsupported and excluded from pass credit.

## Ordinary host workflows

Fresh device captures and OS-injected input, separate from the synthetic fixture:

- Feedback: local draft → captured screenshot → pen/arrow → native label add/edit → side-handle scale/rotate → Undo → body drag → Finish → feedback host. Returned metadata: 1080×1731, 101326 PNG bytes. Reopen and Back kept the prior result
- Attachment: note-card capture → annotation → flat result; re-edit the flattened attachment, add/drag a label, edit it to Reviewed, Finish → attachment host. Final metadata: 986×305, 30388 PNG bytes
- Attachment Cancel: add a temporary stroke to the returned image, then Cancel. An independent comparison of the displayed 986×305 preview before/after cancellation found 0 changed pixels, accounting for its moved screen position

Ordinary-flow evidence is callback success, displayed metadata, screenshots and the adapter's checked PNG encode/decode path. The ordinary in-memory PNG arrays were **not independently extracted or hashed**. External exact-PNG and caller-input checks belong to the synthetic fixture.

Not separately exercised as ordinary UI: duplicate Finish, Cancel during readback, Activity/process recreation or API 29+ pinch. Synthetic checks cover duplicate/pending-close behavior; persistent process recovery is not provided.

## What this does not establish

Physical stylus feel, latency or memory superiority, broad device/API support, independent adoption, consumer demand, developer time savings, redo or editable-project persistence. System-font rendering varies across devices. The two examples are integration evidence, not a stable SDK promise.

The automatic source-only CI job checks integrity and boundaries; the optional manual compile/lint job checks both roots and the 20 host-core cases. Neither reruns the recorded Android runtime checks. The [media](docs/media/README.md) shows actual screenshots, not continuous video evidence or a performance benchmark. Video publication is pending.
