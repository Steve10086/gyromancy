#version 330 core

#moj_import <fog.glsl>

uniform sampler2D IceTexture;
uniform sampler2D SamplerSceneColor;

uniform vec2 ScreenSize;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec4 ColorHDR;
uniform float Refraction;
uniform float Reflection;
uniform float NoiseScale;
uniform vec2 NoiseSpeed;
uniform float NoisePower;
uniform float NoiseThreshold;
uniform float WaveScale;
uniform vec2 WaveSpeed;
uniform float WaveAmount;
uniform float HighlightNoiseScale;
uniform vec2 HighlightNoiseSpeed;
uniform float HighlightNoisePower;
uniform float GameTime;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const float PI = 3.14159265359;
const float TWO_PI = 6.28318530718;

float noise_randomValue(vec2 uv) {
    return fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
}

float valueNoise(vec2 uv) {
    vec2 i = floor(uv);
    vec2 f = fract(uv);
    f = f * f * (3.0 - 2.0 * f);

    vec2 c0 = i + vec2(0.0, 0.0);
    vec2 c1 = i + vec2(1.0, 0.0);
    vec2 c2 = i + vec2(0.0, 1.0);
    vec2 c3 = i + vec2(1.0, 1.0);

    float bottom = mix(noise_randomValue(c0), noise_randomValue(c1), f.x);
    float top = mix(noise_randomValue(c2), noise_randomValue(c3), f.x);
    return mix(bottom, top, f.y);
}

float valueNoiseLoop(vec2 uv, float periodX) {
    vec2 i = floor(uv);
    vec2 f = fract(uv);
    f = f * f * (3.0 - 2.0 * f);

    vec2 c0 = i + vec2(0.0, 0.0);
    vec2 c1 = i + vec2(1.0, 0.0);
    vec2 c2 = i + vec2(0.0, 1.0);
    vec2 c3 = i + vec2(1.0, 1.0);

    c0.x = mod(c0.x, periodX);
    c1.x = mod(c1.x, periodX);
    c2.x = mod(c2.x, periodX);
    c3.x = mod(c3.x, periodX);

    float bottom = mix(noise_randomValue(c0), noise_randomValue(c1), f.x);
    float top = mix(noise_randomValue(c2), noise_randomValue(c3), f.x);
    return mix(bottom, top, f.y);
}

float simpleNoise(vec2 uv, float scale) {
    float t = 0.0;
    for (int i = 0; i < 3; i++) {
        float freq = pow(2.0, float(i));
        float amp = pow(0.5, float(3 - i));
        t += valueNoise(vec2(uv.x * scale / freq, uv.y * scale / freq)) * amp;
    }
    return t + 0.2;
}

float simpleNoiseLoop(vec2 uv, float scale) {
    float t = 0.0;
    for (int i = 0; i < 3; i++) {
        float freq = pow(2.0, float(i));
        float amp = pow(0.5, float(3 - i));
        float periodX = max(1.0, round(scale / freq));
        t += valueNoiseLoop(vec2(uv.x * periodX, uv.y * scale / freq), periodX) * amp;
    }
    return t + 0.2;
}

vec2 radialShear(vec2 uv, vec2 center, vec2 strength, vec2 offset) {
    vec2 delta = uv - center;
    delta.x = sin(delta.x * TWO_PI) / TWO_PI;
    vec2 shear = vec2(delta.y, -delta.x) * dot(delta, delta) * strength;
    return uv + shear + offset;
}

vec2 fireBallUV(vec2 uv) {
    vec2 delta = uv - vec2(0.5);
    delta.x = sin(delta.x * TWO_PI) / TWO_PI;
    return delta + vec2(0.5);
}

vec2 localWave(vec2 uv, float time) {
    vec2 baseUV = fireBallUV(uv);
    float poleMask = sin(clamp(uv.y, 0.0, 1.0) * PI);
    vec2 waveA = baseUV + time * WaveSpeed;
    vec2 waveB = vec2(baseUV.y, baseUV.x) - time * WaveSpeed + vec2(17.13, 29.71);
    vec2 wave = vec2(simpleNoiseLoop(waveA, WaveScale), simpleNoiseLoop(waveB, WaveScale)) - 0.5;
    return uv + wave * WaveAmount * poleMask;
}

void main() {
    float time = GameTime * 1500.0;
    vec2 texCoord = vec2(texCoord0.x, 1.0 - texCoord0.y);
    vec2 waveUV = localWave(texCoord, time);
    vec2 shearUV = radialShear(waveUV, vec2(0.5), vec2(5.0), time * NoiseSpeed);

    float rawNoise = pow(simpleNoiseLoop(shearUV, NoiseScale) * simpleNoiseLoop(waveUV + time * NoiseSpeed, NoiseScale), NoisePower);
    float noise = smoothstep(NoiseThreshold, 1.0, rawNoise);
    float ripple = texture(IceTexture, waveUV).r - 0.5;
    vec2 screenUV = gl_FragCoord.xy / ScreenSize;
        vec2 distortionVector = vec2(
            simpleNoiseLoop(shearUV + vec2(13.17, 29.71), NoiseScale),
            simpleNoiseLoop(waveUV + time * NoiseSpeed + vec2(41.23, 7.91), NoiseScale)
        ) - 0.5;
        vec2 distortion = distortionVector * (noise + ripple) * Refraction * vertexColor.a;
    vec4 sceneColor = texture(SamplerSceneColor, screenUV + distortion);

    vec2 highlightUV = radialShear(waveUV, vec2(0.5), vec2(5.0), time * HighlightNoiseSpeed);
    float highlightNoise = pow(simpleNoiseLoop(highlightUV, HighlightNoiseScale), HighlightNoisePower);
    vec3 highlight = ColorHDR.rgb * ColorHDR.a * highlightNoise * Reflection;
    vec4 tint = vertexColor * ColorModulator;
    vec4 color = vec4((sceneColor.rgb + highlight) * tint.rgb, tint.a);

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
