uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec4 center = texture(Sampler0, texCoord) * vertexColor;
    vec3 halo = vec3(0.0);

    // A small texture-space blur keeps the pass projection-specific while
    // producing a soft light around the transparent stroke material.
    for (int x = -2; x <= 2; x++) {
        for (int y = -2; y <= 2; y++) {
            vec4 sampleColor = texture(Sampler0, texCoord + vec2(x, y) * texel);
            float distanceWeight = 1.0 / (1.0 + float(x * x + y * y));
            halo += sampleColor.rgb * sampleColor.a * distanceWeight;
        }
    }

    vec3 emitted = center.rgb * center.a * 1.35 + halo * 0.10;
    if (max(max(emitted.r, emitted.g), emitted.b) < 0.001) discard;
    // The render type uses ONE/ONE, so this contributes light without
    // replacing the regular half-alpha projection surface.
    fragColor = vec4(emitted, 0.0);
}
