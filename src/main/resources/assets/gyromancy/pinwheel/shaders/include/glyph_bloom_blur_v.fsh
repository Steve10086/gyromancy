// Vertical separable gaussian blur (9 taps, sigma ≈ 2.0)
// Reads from DiffuseSampler0, writes blurred result.

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D DiffuseSampler0;
uniform vec2 ScreenSize; // Veil built-in

void main() {
    vec2 texelSize = ScreenSize;

    // 9-tap gaussian kernel (sigma=2.0, normalized)
    float w[9] = float[9](
        0.0276, 0.0663, 0.1238, 0.1802, 0.2042,
        0.1802, 0.1238, 0.0663, 0.0276
    );

    vec4 sum = vec4(0.0);
    for (int i = 0; i < 9; i++) {
        float offset = float(i - 4) * texelSize.y;
        sum += texture(DiffuseSampler0, texCoord + vec2(0.0, offset)) * w[i];
    }

    fragColor = sum;
}
