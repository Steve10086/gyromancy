// Additive composite for glyph bloom.

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D DiffuseSampler0;

void main() {
    fragColor = texture(DiffuseSampler0, texCoord);
}
