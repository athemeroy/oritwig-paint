/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
package dev.oritwig.markup.core;

import dev.oritwig.markup.platform.Platform;

public class Swatch {

    public int color;
    public float colorLocation;
    public float brushWeight;

    public Swatch(int color, float colorLocation, float brushWeight) {
        this.color = color;
        this.colorLocation = colorLocation;
        this.brushWeight = brushWeight;
    }

    public Swatch clone() {
        return new Swatch(color, colorLocation, brushWeight);
    }
}
