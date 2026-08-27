#version 330 core

#moj_import <fog.glsl>
#moj_import <photon:particle.glsl>
#moj_import <photon:particle_utils.glsl>

uniform sampler2D SamplerCurve;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform float GameTime;
uniform float WobbleSpeed;
uniform float WobbleIntensity;
uniform float WobbleFrequency;
uniform float WobbleAmount;
uniform float CurveWaveAmount;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;

ParticleData getParticleData2() {
    ParticleData data;

    #ifdef PARTICLE_MODEL_INSTANCE
    vec3 camPos = inverse(ModelViewMat)[3].xyz;
    vec3 dirToCam = normalize(camPos - iPos);
    vec3 right = normalize(cross(vec3(0, 1, 0), dirToCam));
    vec3 up = vec3(0, 1, 0);
    vec3 forward = cross(right, up);
    mat3 faceMat = mat3(right, up, forward);

    vec3 centeredPos = aPos - vec3(0.5);
    data.Position = faceMat * (centeredPos * iScale) + iPos;
    data.Normal = normalize(faceMat * aNormal);
    data.Color = vec4(iColor.rgb * aBrightness, iColor.a);
    data.UV = aUV;
    data.LightUV = ivec2((iLight >> 16) & 0xFFFF, iLight & 0xFFFF);
    #endif

    return data;
}

void main() {
    #ifdef PARTICLE_MODEL_INSTANCE
    ParticleData data = getParticleData2();
    #else
    ParticleData data = getParticleData();
    #endif

    vec3 vPos = data.Position;
    vec3 viewPos = (ModelViewMat * vec4(vPos, 1.0)).xyz;
    float wobbleValue = WobbleSpeed * GameTime * 2000.0 + viewPos.y * WobbleIntensity;
    float particleY = data.UV.y;
    #ifdef PARTICLE_INSTANCE
    particleY = aPos.y * 0.5 + 0.5;
    #elif defined(PARTICLE_MODEL_INSTANCE)
    particleY = aPos.y;
    #endif
    float curveWobble = getCurveValue(SamplerCurve, 0, particleY) * CurveWaveAmount;
    vec2 normalPlane = data.Normal.xz;
    normalPlane = length(normalPlane) > 0.0001 ? normalize(normalPlane) : vec2(0.0);
    vec2 curveOffset = normalPlane * curveWobble;
    vec3 wobblePos = vec3(vPos.x + sin(wobbleValue) * WobbleFrequency + curveOffset.x, vPos.y, vPos.z + cos(wobbleValue) * WobbleFrequency + curveOffset.y);
    wobblePos = mix(wobblePos, vPos, WobbleAmount);

    vec4 viewPos4 = ModelViewMat * vec4(wobblePos, 1.0);

    vertexDistance = fog_distance(viewPos4.xyz, FogShape);
    texCoord0 = data.UV;
    vertexColor = data.Color;

    gl_Position = ProjMat * viewPos4;
}
