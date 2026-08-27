#version 330 core

#moj_import <fog.glsl>
#moj_import <photon:particle_utils.glsl>

uniform sampler2D SamplerCurve;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec4 FrontHDR;
uniform vec4 ColorHDR;
uniform float Dissolve;
uniform float NoiseScale;
uniform vec2 NoiseSpeed;
uniform float NoisePower;
uniform float GlobalAlpha;
uniform float GameTime;
uniform float TwirlAmount;
uniform vec2 TwirlCenter;
uniform vec2 TwirlSpeed;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;
in float particleT;
//in  vec3  vViewPos;
//in  vec3  vViewNormal;

out vec4 fragColor;


// ------------------------------------------------------------
// Fresnel effect (Schlick-like) utility
//  ‣  Normal   : surface normal   (any space, MUST be normalized later)
//  ‣  ViewDir  : direction TO camera (same space as Normal)
//  ‣  power    : controls edge fall-off
//  ←  return   : Fresnel term (0‥1)
// ------------------------------------------------------------
float FresnelEffect(vec3 Normal, vec3 ViewDir, float power)
{
    float cosTheta = clamp(dot(normalize(Normal), normalize(ViewDir)), 0.0, 1.0);
    return pow(1.0 - cosTheta, power);
}

// 随机值生成（哈希函数）
float noise_randomValue(vec2 uv) {
    return fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
}

// 插值函数
float noise_interpolate(float a, float b, float t) {
    return mix(a, b, t); // GLSL 内建线性插值函数
}

// 单层值噪声
float valueNoise(vec2 uv, float periodX) {
    vec2 i = floor(uv);
    vec2 f = fract(uv);
    f = f * f * (3.0 - 2.0 * f); // Hermite 插值

    // 采样 4 个邻居格点
    vec2 c0 = i + vec2(0.0, 0.0);
    vec2 c1 = i + vec2(1.0, 0.0);
    vec2 c2 = i + vec2(0.0, 1.0);
    vec2 c3 = i + vec2(1.0, 1.0);

    c0.x = mod(c0.x, periodX);
    c1.x = mod(c1.x, periodX);
    c2.x = mod(c2.x, periodX);
    c3.x = mod(c3.x, periodX);

    float r0 = noise_randomValue(c0);
    float r1 = noise_randomValue(c1);
    float r2 = noise_randomValue(c2);
    float r3 = noise_randomValue(c3);

    float bottom = mix(r0, r1, f.x);
    float top    = mix(r2, r3, f.x);
    float t      = mix(bottom, top, f.y);
    return t;
}

// 主函数：SimpleNoise，叠加 3 层值噪声
float simpleNoise(vec2 uv, float scale) {
    float t = 0.0;
    for(int i = 0; i < 3; i++)
    {
        float freq = pow(2.0, float(i));
        float amp = pow(0.5, float(3-i));
        float periodX = max(1.0, round(scale / freq));
        t += valueNoise(vec2(uv.x * periodX, uv.y * scale / freq), periodX) * amp;
    }
    return t + 0.2;
}

float remap(float inputValue, vec2 inMinMax, vec2 outMinMax) {
    return outMinMax.x + (inputValue - inMinMax.x) * (outMinMax.y - outMinMax.x) / (inMinMax.y - inMinMax.x);
}

vec2 radialShear(vec2 uv, vec2 center, vec2 strength, vec2 offset) {
    vec2 delta = uv - center;
    delta.x = sin(delta.x * 6.28318530718) / 6.28318530718;
    float delta2 = dot(delta, delta);  // 距离平方
    vec2 delta_offset = delta2 * strength;

    vec2 shear = vec2(delta.y, -delta.x) * delta_offset;

    return uv + shear + offset;
}

vec2 twirl(vec2 uv, vec2 center, float strength, vec2 offset) {
    vec2 delta = uv - center;
    delta.x = sin(delta.x * 6.28318530718) / 6.28318530718;
    float angle = strength * length(delta); // 旋转角度随半径变化

    float s = sin(angle);
    float c = cos(angle);

    float x = c * delta.x - s * delta.y;
    float y = s * delta.x + c * delta.y;

    return vec2(x + center.x + offset.x, y + center.y + offset.y);
}

void main() {
    float time = GameTime * 1500;
    vec2 offset = time * NoiseSpeed;
    vec2 texCoord = vec2(texCoord0.x, 1 - texCoord0.y);
    vec2 uv = radialShear(texCoord, vec2(0.5, 0.5), vec2(5, 5), offset);
    float noise = simpleNoise(uv, NoiseScale);

    vec2 twirlUV = twirl(texCoord, TwirlCenter, TwirlAmount, TwirlSpeed * time);
    float noise2 = simpleNoise(twirlUV, NoiseScale);
    float mergeNoise = pow(noise2 * noise, NoisePower);

    vec4 tint = vertexColor * ColorModulator;
    vec3 color = ColorHDR.rgb * tint.rgb * ColorHDR.a * mergeNoise;

//    color.rgb *= ColorHDR.rgb * ColorHDR.a;
    float dissolve = remap(getCurveValue(SamplerCurve, 0, particleT), vec2(0, 1), vec2(1, 0));
    if (dissolve < mergeNoise) discard;

    fragColor = linear_fog(vec4(color, GlobalAlpha * tint.a), vertexDistance, FogStart, FogEnd, FogColor);
}
