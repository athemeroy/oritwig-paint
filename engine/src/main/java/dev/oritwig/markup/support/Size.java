/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package dev.oritwig.markup.support;

import dev.oritwig.markup.platform.Platform;

public class Size {
    public float width;
    public float height;
    public boolean full;

    public Size() {

    }

    public Size(float width, float height) {
        this.width = width;
        this.height = height;
    }
}

