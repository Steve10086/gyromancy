uniform sampler2D DiffuseSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 ray = texture(DiffuseSampler, texCoord);
    float density = max(max(ray.r, ray.g), ray.b);
    vec3 compressed = ray.rgb / (1.0 + density * 0.75);
    fragColor = vec4(compressed, 0.0);
}
