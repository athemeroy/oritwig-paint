/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
package dev.oritwig.markup.core;

import dev.oritwig.markup.platform.Platform;

import android.graphics.RectF;


import dev.oritwig.markup.support.DispatchQueue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Slice {
    private final RectF bounds;
    private final int texture;
    private File file;

    public Slice(final ByteBuffer data, int tex, RectF rect, File cacheDir) {
        bounds = rect;
        texture = tex;

        try {
            File outputDir = cacheDir;
            file = File.createTempFile("paint", ".bin", outputDir);
        } catch (Exception e) {
            Platform.log(e);
        }

        if (file == null)
            return;

        storeData(data);
    }

    public void cleanResources() {
        if (file != null) {
            file.delete();
            file = null;
        }
    }

    private void storeData(ByteBuffer data) {
        try {
            // Portability adapter: GL readback may be a direct, non-array-backed buffer.
            final byte[] input = new byte[data.remaining()];
            data.duplicate().get(input);
            FileOutputStream fos = new FileOutputStream(file);

            final Deflater deflater = new Deflater(Deflater.BEST_SPEED, true);
            deflater.setInput(input, 0, input.length);
            deflater.finish();

            byte[] buf = new byte[1024];
            while (!deflater.finished()) {
                int byteCount = deflater.deflate(buf);
                fos.write(buf, 0, byteCount);
            }
            deflater.end();

            fos.close();
        } catch (Exception e) {
            Platform.log(e);
        }
    }

    public ByteBuffer getData() {
        try {
            byte[] input = new byte[1024];
            byte[] output = new byte[1024];
            FileInputStream fin = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Inflater inflater = new Inflater(true);

            while (true) {
                int numRead = fin.read(input);
                if (numRead == -1 && !inflater.finished()) throw new java.io.IOException("Truncated snapshot");
                if (numRead != -1) inflater.setInput(input, 0, numRead);

                int numDecompressed;
                while ((numDecompressed = inflater.inflate(output, 0, output.length)) != 0) {
                    bos.write(output, 0, numDecompressed);
                }

                if (inflater.finished()) {
                    break;
                } else if (inflater.needsInput()) {
                    continue;
                }
            }

            inflater.end();
            ByteBuffer result = ByteBuffer.wrap(bos.toByteArray(), 0, bos.size());

            bos.close();
            fin.close();

            return result;
        } catch (Exception e) {
            Platform.log(e);
        }

        return null;
    }

    public int getX() {
        return (int) bounds.left;
    }

    public int getY() {
        return (int) bounds.top;
    }

    public int getWidth() {
        return (int) bounds.width();
    }

    public int getHeight() {
        return (int) bounds.height();
    }

    public RectF getBounds() {
        return new RectF(bounds);
    }

    public int getTexture() {
        return texture;
    }
}
