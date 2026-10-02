# Source, changes and licenses

Upstream: [Telegram for Android](https://github.com/DrKLO/Telegram/tree/f2908b14133bbffbf7ab04f641ecb5bfaf533242), exact commit `f2908b14133bbffbf7ab04f641ecb5bfaf533242`.

- [source-ledger.json](source-ledger.json): each retained Java file, exact upstream path/blob, reviewed hash and adaptation description
- [module-source-lock.json](module-source-lock.json): all 37 engine build/source/resource inputs by SHA-256
- [authored-ledger.json](authored-ledger.json): exact source-host, test, build, documentation, verification and media inventory outside the engine; explicitly identifies generated Gradle tooling and duplicated engine inputs
- [publication-source-delta.json](publication-source-delta.json): the two-host PNG helper extraction since the recorded runtime binaries, with before/after hashes
- [tested-artifacts.json](tested-artifacts.json): identity of the two APKs, AAR and fixture used for the recorded checks; binaries are excluded from Git
- [license-review/REVIEW.md](license-review/REVIEW.md): the narrow source/asset license boundary and primary links

## Retained behavior

Telegram supplies pressure interpolation, stroke smoothing and curved-arrow geometry; GL stamp generation and composition; compressed raster undo; plain-text layout/measurement, entity selection and transforms; and plain-text flatten transform order. The five retained shader strings and the single radial brush image are unchanged. No original full Telegram checkout or omitted code is distributed.

## Oritwig work

Dated 2026-10-02; exact per-file descriptions are in the source ledger.

- `MarkupSession`: explicit Context/cache/input ownership, source/screen coordinate boundary and primitive ordinary-host API
- All-entity gesture transaction snapshots, exact Undo/CANCEL and label editing
- Asynchronous commit-aware Finish, lifecycle/failure callbacks and cancellation of stale work
- Raw texture snapshots for exact same-session surface recovery; stable text export composition
- Guarded compressed snapshot reads, synchronized UndoStore and explicit intended brush-pass alpha blend state
- Narrow platform substitutions for Telegram globals, AndroidX primitives and the ZXing distance call, using Android/Java APIs
- Two platform-UI hosts: screenshot and note-attachment acquisition, fitted viewport, toolbar/dialogs, background PNG encode/decode validation, callback/result ownership and failure/cancel handling

No replacement pressure, stroke-smoothing, arrow, shader or entity-geometry algorithm is claimed as original work.

## Excluded

The full iq-attributed shape-distance shader and its registration, manual shapes/recognizer/models, elliptical/neon brushes, blur/redaction, eraser/fill, stickers/emoji, bundled fonts, native/JNI algorithms, account/protocol/network code and original cursor/service infrastructure. No deletion patch containing the excluded shader is distributed.

The independent consumer contains a byte-identical engine source copy; it is not a second component. Generated Gradle wrapper files retain their original notices and are build tooling, not runtime dependencies. Root LICENSE/NOTICE and both engine asset notices are preserved.
