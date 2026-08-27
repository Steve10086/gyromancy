#version 150

uniform float GameTime;
uniform vec3 ColorA;
uniform vec3 ColorB;
uniform float Alpha;

in vec2 texCoord;

out vec4 fragColor;

vec4 sampleGradient(float t) {
    vec3 colorA = ColorA;
    vec3 colorB = ColorB;
    float posA = 0.0;
    float posB = 0.9369802;

    float clamped = clamp(t, posA, posB);
    float lerpFactor = (clamped - posA) / (posB - posA);
    vec3 color = mix(colorA, colorB, lerpFactor);

    return vec4(color, 1.0);
}

float noise_randomValue(vec2 uv) {
    return fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
}

float noise_interpolate(float a, float b, float t) {
    return mix(a, b, t);
}

float valueNoise(vec2 uv) {
    vec2 i = floor(uv);
    vec2 f = fract(uv);
    f = f * f * (3.0 - 2.0 * f);

    vec2 c0 = i + vec2(0.0, 0.0);
    vec2 c1 = i + vec2(1.0, 0.0);
    vec2 c2 = i + vec2(0.0, 1.0);
    vec2 c3 = i + vec2(1.0, 1.0);

    float r0 = noise_randomValue(c0);
    float r1 = noise_randomValue(c1);
    float r2 = noise_randomValue(c2);
    float r3 = noise_randomValue(c3);

    float bottom = mix(r0, r1, f.x);
    float top = mix(r2, r3, f.x);
    float t = mix(bottom, top, f.y);
    return t;
}

float simpleNoiseDeterministic(vec2 uv, float scale) {
    float t = 0.0;
    for (int i = 0; i < 3; i++) {
        float freq = pow(2.0, float(i));
        float amp = pow(0.5, float(3 - i));
        t += valueNoise(vec2(uv.x * scale / freq, uv.y * scale / freq)) * amp;
    }
    return t + 0.2;
}

vec4 posterize(vec4 In, vec4 Steps) {
    return floor(In * Steps) / Steps;
}

void main() {
    vec2 ndcPos = texCoord;
    ndcPos.y = 1.0 - ndcPos.y;

    float screenR = ndcPos.x;

    vec4 gradientColor = sampleGradient(screenR);

    vec4 baseColor = vec4(0.3, 0.3, 0.3, 1.0);
    vec4 subtractResult = baseColor - gradientColor;

    vec2 vector2 = vec2(-1.0, 0.0);
    vec2 timeMul = vec2(GameTime * 2000) * vector2;
    vec2 noiseUV = ndcPos + timeMul;
    float noiseVal = simpleNoiseDeterministic(noiseUV, 45.0);

    float sqrtNoise = sqrt(max(noiseVal, 0.0));
    vec4 sqrtNoiseVec = vec4(sqrtNoise, sqrtNoise, sqrtNoise, sqrtNoise);

    vec4 maxResult = max(subtractResult, sqrtNoiseVec);

    vec4 finalSub = maxResult - subtractResult;

    vec4 steps = vec4(1.0, 1.0, 1.0, 2.0);
    vec4 posterized = posterize(finalSub, steps);

    fragColor = vec4(posterized.rgb, Alpha);
}
