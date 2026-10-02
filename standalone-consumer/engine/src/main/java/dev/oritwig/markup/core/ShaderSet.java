/* Modified 2026-10-01 for the bounded Oritwig Markup source module.
 * Derived from Telegram f2908b14133bbffbf7ab04f641ecb5bfaf533242.
 * Original authorship/license notices are retained; adaptations: provenance/source-ledger.json. */
package dev.oritwig.markup.core;

import dev.oritwig.markup.platform.Platform;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ShaderSet {

    private static final Map<String, Map<String, Object>> AVAILABLE_SHADERS = createMap();

    private static final String VERTEX = "vertex";
    private static final String FRAGMENT = "fragment";
    private static final String ATTRIBUTES = "attributes";
    private static final String UNIFORMS = "uniforms";

    private static final String PAINT_BRUSH_VSH =
        "precision highp float;" +

        "uniform mat4 mvpMatrix;" +
        "attribute vec4 inPosition;" +
        "attribute vec2 inTexcoord;" +
        "attribute float alpha;" +
        "varying vec2 varTexcoord;" +
        "varying float varIntensity;" +

        "void main (void) {" +
        "   gl_Position = mvpMatrix * inPosition;" +
        "   varTexcoord = inTexcoord;" +
        "   varIntensity = alpha;" +
        "}";
    private static final String PAINT_BRUSH_FSH =
        "precision highp float;" +

        "varying vec2 varTexcoord;" +
        "varying float varIntensity;" +
        "uniform sampler2D texture;" +

        "void main (void) {" +
        "   gl_FragColor = vec4(1, 1, 1, varIntensity * texture2D(texture, varTexcoord.st, 0.0).r);" +
        "}";
    private static final String PAINT_BLIT_VSH =
        "precision highp float;" +

        "uniform mat4 mvpMatrix;" +
        "attribute vec4 inPosition;" +
        "attribute vec2 inTexcoord;" +
        "varying vec2 varTexcoord;" +

        "void main (void) {" +
        "   gl_Position = mvpMatrix * inPosition;" +
        "   varTexcoord = inTexcoord;" +
        "}";
    private static final String PAINT_BLIT_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "void main (void) {" +
        "   gl_FragColor = texture2D(texture, varTexcoord.st, 0.0);" +
        "   gl_FragColor.rgb *= gl_FragColor.a;" +
        "}";
    private static final String PAINT_BLITWITHMASK_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D otexture;" +
        "uniform sampler2D mask;" +
        "uniform vec4 color;" +
        "void main (void) {" +
        "   vec4 dst = texture2D(texture, varTexcoord.st, 0.0);" +
        "   float srcAlpha = color.a * texture2D(mask, varTexcoord.st, 0.0).a;" +
        "   float outAlpha = srcAlpha + dst.a * (1.0 - srcAlpha);" +
        "   gl_FragColor.rgb = (color.rgb * srcAlpha + dst.rgb * dst.a * (1.0 - srcAlpha));" +
        "   gl_FragColor.a = outAlpha;" +
        "}";
    private static final String PAINT_COMPOSITEWITHMASK_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D mask;" +
        "uniform vec4 color;" +
        "void main(void) {" +
        "   vec4 dst = texture2D(texture, varTexcoord.st, 0.0);" +
        "   float srcAlpha = color.a * texture2D(mask, varTexcoord.st, 0.0).a;" +
        "   float outAlpha = srcAlpha + dst.a * (1.0 - srcAlpha);" +
        "   gl_FragColor.rgb = (color.rgb * srcAlpha + dst.rgb * dst.a * (1.0 - srcAlpha)) / outAlpha;" +
        "   gl_FragColor.a = outAlpha;" +
        "}";
    private static final String PAINT_NONPREMULTIPLIEDBLIT_FSH =
        "precision highp float;" +

        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +

        "void main (void) {" +
        "   gl_FragColor = texture2D(texture, varTexcoord.st, 0.0);" +
        "}";

    private static Map<String, Map<String, Object>> createMap() {
        Map<String, Map<String, Object>> result = new HashMap<>();

        Map<String, Object> shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BRUSH_VSH);
        shader.put(FRAGMENT, PAINT_BRUSH_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord", "alpha"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture"});
        result.put("brush", Collections.unmodifiableMap(shader));

        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_BLIT_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "alpha"});
        result.put("blit", Collections.unmodifiableMap(shader));

        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_BLITWITHMASK_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "mask", "color"});
        result.put("blitWithMask", Collections.unmodifiableMap(shader));

        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_COMPOSITEWITHMASK_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "mask", "color"});
        result.put("compositeWithMask", Collections.unmodifiableMap(shader));


        // undo
        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_NONPREMULTIPLIEDBLIT_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture"});
        result.put("nonPremultipliedBlit", Collections.unmodifiableMap(shader));
        return Collections.unmodifiableMap(result);
    }

    public static Map<String, Shader> setup() {
        Map<String, Shader> result = new HashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : AVAILABLE_SHADERS.entrySet()) {
            Map<String, Object> value = entry.getValue();

            String vertex = (String) value.get(VERTEX);
            String fragment = (String) value.get(FRAGMENT);
            String[] attributes = (String[]) value.get(ATTRIBUTES);
            String[] uniforms = (String[]) value.get(UNIFORMS);

            Shader shader = new Shader(vertex, fragment, attributes, uniforms);
            result.put(entry.getKey(), shader);
        }

        return Collections.unmodifiableMap(result);
    }
}
