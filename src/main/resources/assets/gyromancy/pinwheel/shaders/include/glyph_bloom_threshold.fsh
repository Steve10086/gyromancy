// Glyph bloom threshold shader
// Detects glyph pixels in the main scene by their specific RGB colors
// and outputs a bloom mask for blurring.

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D DiffuseSampler0;

void main() {
    vec4 color = texture(DiffuseSampler0, texCoord);

    // Glyph colors from GlyphImageProvider (exact values)
    // Fire:  1.0, 0.0, 0.0      (Red)
    // Water: 0.0, 0.0, 1.0      (Blue)
    // Earth: 0.545, 0.271, 0.075 (Saddle Brown)
    const vec3 glyphFire  = vec3(1.0, 0.0, 0.0);
    const vec3 glyphWater = vec3(0.0, 0.0, 1.0);
    const vec3 glyphEarth = vec3(0.545, 0.271, 0.075);

    const float threshold = 0.1;
    bool isGlyph = (distance(color.rgb, glyphFire)  < threshold)
                || (distance(color.rgb, glyphWater) < threshold)
                || (distance(color.rgb, glyphEarth) < threshold);

    fragColor = isGlyph ? vec4(color.rgb, 1.0) : vec4(0.0);
}
