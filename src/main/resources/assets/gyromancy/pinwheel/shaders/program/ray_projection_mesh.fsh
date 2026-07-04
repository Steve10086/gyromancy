in vec4 beamColor;
in float beamT;

out vec4 fragColor;

void main() {
    vec4 color = beamColor;
    color.a *= 1.0 - clamp(beamT, 0.0, 1.0);
    if (color.a < 0.01) {
        discard;
    }
    color.rgb *= color.a;
    fragColor = color;
}
