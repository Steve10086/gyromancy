// Glyph bloom composite shader
// Passes through bloom pixels. Additive blend (ONE, ONE) set in program JSON.

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D DiffuseSampler0;

void main() {
    fragColor = texture(DiffuseSampler0, texCoord);
}
