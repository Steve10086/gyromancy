uniform sampler2D Sampler0;
uniform float GlowStrength;

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord) * vertexColor;
    if (tex.a < 0.01) {
        discard;
    }

    fragColor = vec4(tex.rgb * tex.a * (1.0 + GlowStrength), tex.a);
}
