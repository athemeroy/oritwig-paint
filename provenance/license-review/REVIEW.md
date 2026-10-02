# Bounded source and asset license review

Reviewed 2026-10-01; publication presentation updated 2026-10-02. This is an engineering provenance assessment, not a legal opinion.

## Source grant

[Telegram's application catalogue](https://telegram.org/apps) identifies Android as GNU GPL v2 or later and links the official DrKLO/Telegram repository. The exact [upstream LICENSE](https://github.com/DrKLO/Telegram/blob/f2908b14133bbffbf7ab04f641ecb5bfaf533242/LICENSE) is GPL-2.0 text, blob `d159169d1050894d3ea3b98e1c965c4058208fe1`. The original text is retained in [LICENSE-telegram-GPL2.txt](../LICENSE-telegram-GPL2.txt); GPL-3.0 is selected for the combined module.

Headerless Paint files are inside that licensed repository. Retained Size, RectOld and DispatchQueue additionally carry explicit GPL-2.0-or-later notices, preserved intact. No contrary third-party notice was found in the included source branches. Per-file paths, blob IDs and adaptations are in [source-ledger.json](../source-ledger.json).

## Sole bundled runtime artwork

[paint_radial_brush.webp](https://github.com/DrKLO/Telegram/blob/f2908b14133bbffbf7ab04f641ecb5bfaf533242/TMessagesProj/src/main/res/drawable/paint_radial_brush.webp), 256×256, exact upstream blob `6788001f9648e6f241b6a50474be5d78786e988a` and SHA-256 `d835d955dfe82e752ef119556a18303adb4ef696b72a6139447743657e7073c5`.

The WebP path begins at [63f86ef18436f84127519307e76f2b51e593b279](https://github.com/DrKLO/Telegram/commit/63f86ef18436f84127519307e76f2b51e593b279). Its PNG predecessor was introduced by DrKLO in [e313885ac540c31ea02c85122907a5845fc576d2](https://github.com/DrKLO/Telegram/commit/e313885ac540c31ea02c85122907a5845fc576d2), with original blob `c9e6c60309629df18c8158df164fb09346d534c8`. Both decode to identical RGBA pixels, SHA-256 `08f1a17743c93e05c03d0be431610920020df97ded58382a73788c894bc91f75`.

**No separate per-asset license or external author attribution was found.** Inclusion relies on the official repository/application-wide GPL grant. This is an explicit scope inference, not a separate author permission. If your distribution review requires an asset-specific grant, that remains unresolved. Pen and stroke-arrow use this same resource; there is no second arrow image.

## Narrow boundary

Only that radial image is included. Elliptical/neon assets, `shapes.dat`, fonts, icons, stickers and emoji are excluded. Their permissions are not inferred from this review. The complete iq-attributed `PAINT_SHAPE_FSH`, its registration, shape geometry and recognition paths are omitted; no patch embeds those excluded bodies.

No external runtime Maven library is linked. Java `Math.hypot` and primitive platform adapters replace ZXing/AndroidX calls without bundling those libraries. The tiny retained `lerp`/`lerpAngle` helpers originate in [AndroidUtilities.java](https://github.com/DrKLO/Telegram/blob/f2908b14133bbffbf7ab04f641ecb5bfaf533242/TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java), blob `083d15ee2e8185a6067091313d79d135c1b8c43b`. Android system fonts/EditText replace bundled fonts and custom cursor infrastructure.

Build tooling has its own upstream notices, retained in the generated Gradle wrapper. No third-party mirror or MIT-labelled copy was used to license Telegram source or artwork.
