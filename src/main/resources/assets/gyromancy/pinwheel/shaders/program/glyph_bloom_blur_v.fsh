// Vertical separable gaussian blur.

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D DiffuseSampler0;
uniform vec2 OutSize;

void main() {
    const float weights[9] = float[9](
            0.0276, 0.0663, 0.1238, 0.1802, 0.2042,
            0.1802, 0.1238, 0.0663, 0.0276);
    vec2 texelSize = 1.0 / OutSize;
    vec4 sum = vec4(0.0);
    for (int i = 0; i < 9; i++) {
        float offset = float(i - 4) * texelSize.y;
        sum += texture(DiffuseSampler0, texCoord + vec2(0.0, offset)) * weights[i];
    }
    fragColor = sum;
}
